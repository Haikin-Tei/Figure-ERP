package com.figureerp.module.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.product.entity.FigureProduct;
import com.figureerp.module.product.mapper.FigureProductMapper;
import com.figureerp.module.stock.converter.InboundConverter;
import com.figureerp.module.stock.converter.InboundStatus;
import com.figureerp.module.stock.converter.InboundType;
import com.figureerp.module.stock.dto.InboundItemRequest;
import com.figureerp.module.stock.dto.InboundQuery;
import com.figureerp.module.stock.dto.InboundSaveRequest;
import com.figureerp.module.stock.entity.Area;
import com.figureerp.module.stock.entity.InboundItem;
import com.figureerp.module.stock.entity.InboundOrder;
import com.figureerp.module.stock.entity.Shelf;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.mapper.AreaMapper;
import com.figureerp.module.stock.mapper.InboundItemMapper;
import com.figureerp.module.stock.mapper.InboundOrderMapper;
import com.figureerp.module.stock.mapper.ShelfMapper;
import com.figureerp.module.stock.mapper.WarehouseMapper;
import com.figureerp.module.stock.vo.InboundOrderVO;
import com.figureerp.module.stock.vo.InboundSummaryVO;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 入库记录的查询与维护。
 * <p>
 * 业务规则：
 * <ul>
 *   <li>入库单必须挂在已存在的仓库下；区域 / 货架可空，填了就必须是同仓库的；</li>
 *   <li>单号留空自动生成（RK + 日期 + 4 位序号），手填则未删除记录内唯一；</li>
 *   <li>明细至少一条，同一款手办不能在一张单里重复出现；数量必须大于 0；</li>
 *   <li>数量合计、金额合计一律由后端按明细重算，不信任前端传值；</li>
 *   <li>只有「待入库」状态可以编辑和删除；已入库只能作废；已作废不能再确认；</li>
 *   <li>查询所有登录用户可用，增删改 / 确认 / 作废需要管理员或主管。</li>
 * </ul>
 * 注意：目前还没有 stock 库存表，确认入库只改单据状态，不累加库存；等库存表落地后再在此处补扣加逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InboundOrderMapper orderMapper;
    private final InboundItemMapper itemMapper;
    private final WarehouseMapper warehouseMapper;
    private final AreaMapper areaMapper;
    private final ShelfMapper shelfMapper;
    private final FigureProductMapper productMapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<InboundOrderVO> page(InboundQuery q) {
        LambdaQueryWrapper<InboundOrder> w = Wrappers.lambdaQuery();

        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            // 手办名称在明细表里，先按明细反查单号，再和主表字段用 OR 串起来
            Set<Long> itemOrderIds = findOrderIdsByProductName(kw);
            w.and(x -> x.like(InboundOrder::getInboundNo, kw)
                    .or().like(InboundOrder::getSupplier, kw)
                    .or().like(InboundOrder::getOperator, kw)
                    .or(!itemOrderIds.isEmpty(), y -> y.in(InboundOrder::getId, itemOrderIds)));
        }
        if (q.getWarehouseId() != null) {
            w.eq(InboundOrder::getWarehouseId, q.getWarehouseId());
        }
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(InboundOrder::getStatus, q.getStatus());
        }
        if (StringUtils.hasText(q.getInboundType())) {
            w.eq(InboundOrder::getInboundType, q.getInboundType());
        }
        // 日期按「含当天」处理：起始 >= 当天 00:00，结束 < 次日 00:00
        if (q.getStartDate() != null) {
            w.ge(InboundOrder::getInboundTime, q.getStartDate().atStartOfDay());
        }
        if (q.getEndDate() != null) {
            w.lt(InboundOrder::getInboundTime, q.getEndDate().plusDays(1).atStartOfDay());
        }

        // 入库时间倒序；同一天的按 id 倒序，保证新单在前、多次查询顺序稳定
        w.orderByDesc(InboundOrder::getInboundTime).orderByDesc(InboundOrder::getId);

        Page<InboundOrder> page = orderMapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        List<InboundOrder> records = page.getRecords();

        Map<Long, List<InboundItem>> itemsByOrder = loadItems(records);
        Map<Long, Warehouse> warehouses = loadWarehouses(records);
        Map<Long, Area> areas = loadAreas(records);
        Map<Long, Shelf> shelves = loadShelves(records);

        // 注意：区域 / 货架可空，必须先判 null 再查。
        // Map.of() 是不可变集合，拿 null 当 key 去 get 会直接抛 NPE（不是返回 null），
        // 一页单据都没填区域时就会踩到。
        return PageResult.of(page, o -> InboundConverter.toVO(o,
                warehouses.get(o.getWarehouseId()),
                o.getAreaId() == null ? null : areas.get(o.getAreaId()),
                o.getShelfId() == null ? null : shelves.get(o.getShelfId()),
                itemsByOrder.getOrDefault(o.getId(), List.of()),
                false));
    }

    public InboundOrderVO detail(Long id) {
        InboundOrder order = getExisting(id);
        List<InboundItem> items = listItems(id);
        return InboundConverter.toVO(order,
                warehouseMapper.selectById(order.getWarehouseId()),
                order.getAreaId() == null ? null : areaMapper.selectById(order.getAreaId()),
                order.getShelfId() == null ? null : shelfMapper.selectById(order.getShelfId()),
                items, true);
    }

    /** 顶部统计卡：待入库全量 + 本月已入库的单数 / 件数 / 金额 */
    public InboundSummaryVO summary() {
        InboundSummaryVO vo = new InboundSummaryVO();

        vo.setPendingCount(orderMapper.selectCount(Wrappers.<InboundOrder>lambdaQuery()
                .eq(InboundOrder::getStatus, InboundStatus.DRAFT)));

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = monthStart.plusMonths(1);

        List<InboundOrder> monthly = orderMapper.selectList(Wrappers.<InboundOrder>lambdaQuery()
                .eq(InboundOrder::getStatus, InboundStatus.CONFIRMED)
                .ge(InboundOrder::getInboundTime, monthStart)
                .lt(InboundOrder::getInboundTime, nextMonthStart));

        long quantity = 0L;
        BigDecimal amount = BigDecimal.ZERO;
        for (InboundOrder o : monthly) {
            quantity += o.getTotalQuantity() == null ? 0 : o.getTotalQuantity();
            amount = amount.add(o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount());
        }
        vo.setMonthOrderCount((long) monthly.size());
        vo.setMonthQuantity(quantity);
        vo.setMonthAmount(amount.setScale(2, RoundingMode.HALF_UP));
        return vo;
    }

    /* ================= 维护 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(InboundSaveRequest req) {
        SysUser user = currentUserService.requireManager("维护入库记录");

        requireWarehouse(req.getWarehouseId());
        requireArea(req.getAreaId(), req.getWarehouseId());
        requireShelf(req.getShelfId(), req.getWarehouseId());

        List<InboundItem> items = buildItems(req.getItems());
        String inboundNo = StringUtils.hasText(req.getInboundNo())
                ? req.getInboundNo().trim()
                : generateInboundNo();
        checkNoUnique(inboundNo, null);

        InboundOrder order = new InboundOrder();
        order.setInboundNo(inboundNo);
        order.setInboundType(StringUtils.hasText(req.getInboundType()) ? req.getInboundType() : InboundType.PURCHASE);
        order.setWarehouseId(req.getWarehouseId());
        order.setAreaId(req.getAreaId());
        order.setShelfId(req.getShelfId());
        order.setSupplier(trimToNull(req.getSupplier()));
        order.setStatus(InboundStatus.DRAFT);
        order.setInboundTime(req.getInboundTime() == null
                ? LocalDateTime.now().withNano(0)
                : req.getInboundTime().withNano(0));
        order.setOperator(StringUtils.hasText(req.getOperator())
                ? req.getOperator().trim()
                : user.getRealName());
        order.setRemark(trimToNull(req.getRemark()));
        fillTotals(order, items);

        orderMapper.insert(order);
        saveItems(order.getId(), items);

        log.info("新增入库单: id={} no={} 仓库={} 明细={} 件数={} 金额={}", order.getId(), inboundNo,
                order.getWarehouseId(), items.size(), order.getTotalQuantity(), order.getTotalAmount());
        return order.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InboundSaveRequest req) {
        currentUserService.requireManager("维护入库记录");
        InboundOrder existing = getExisting(id);
        requireDraft(existing, "编辑");

        requireWarehouse(req.getWarehouseId());
        requireArea(req.getAreaId(), req.getWarehouseId());
        requireShelf(req.getShelfId(), req.getWarehouseId());

        List<InboundItem> items = buildItems(req.getItems());
        String inboundNo = StringUtils.hasText(req.getInboundNo())
                ? req.getInboundNo().trim()
                : existing.getInboundNo();
        checkNoUnique(inboundNo, id);

        InboundOrder order = new InboundOrder();
        order.setId(id);
        order.setInboundNo(inboundNo);
        order.setInboundType(StringUtils.hasText(req.getInboundType()) ? req.getInboundType() : InboundType.PURCHASE);
        order.setWarehouseId(req.getWarehouseId());
        order.setAreaId(req.getAreaId());
        order.setShelfId(req.getShelfId());
        order.setSupplier(trimToNull(req.getSupplier()));
        order.setInboundTime(req.getInboundTime() == null
                ? existing.getInboundTime()
                : req.getInboundTime().withNano(0));
        // 经办人留空时保持原经办人，不要因为编辑把历史责任人抹掉
        order.setOperator(StringUtils.hasText(req.getOperator())
                ? req.getOperator().trim()
                : existing.getOperator());
        order.setRemark(trimToNull(req.getRemark()));
        fillTotals(order, items);
        orderMapper.updateById(order);

        // 明细整体替换：先逻辑删除旧行再插新行，保证明细与提交内容完全一致
        itemMapper.delete(Wrappers.<InboundItem>lambdaQuery().eq(InboundItem::getInboundId, id));
        saveItems(id, items);

        log.info("编辑入库单: id={} no={} 明细={} 件数={} 金额={}", id, inboundNo,
                items.size(), order.getTotalQuantity(), order.getTotalAmount());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        currentUserService.requireManager("删除入库单");
        InboundOrder existing = getExisting(id);
        requireDraft(existing, "删除");

        orderMapper.deleteById(id);
        itemMapper.delete(Wrappers.<InboundItem>lambdaQuery().eq(InboundItem::getInboundId, id));
        log.info("删除入库单: id={} no={}", id, existing.getInboundNo());
    }

    /** 待入库 → 已入库 */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        currentUserService.requireManager("确认入库");
        InboundOrder existing = getExisting(id);
        if (!InboundStatus.DRAFT.equals(existing.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "只有「待入库」的单据可以确认，当前状态：" + InboundStatus.labelOf(existing.getStatus()));
        }
        if (listItems(id).isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该入库单没有手办明细，不能确认入库");
        }

        // 只 set 状态这一列，不要用空实体 updateById：
        // 供应商 / 区域 / 货架 / 备注标了 FieldStrategy.ALWAYS，空实体更新会把它们一起写成 NULL，
        // 等于确认入库时把单据信息抹掉（这个坑踩过一次）。
        LambdaUpdateWrapper<InboundOrder> w = Wrappers.<InboundOrder>lambdaUpdate()
                .eq(InboundOrder::getId, id)
                .set(InboundOrder::getStatus, InboundStatus.CONFIRMED);
        // 确认时若还没填入库时间，用当前时间补上，保证「本月入库」统计能算到
        if (existing.getInboundTime() == null) {
            w.set(InboundOrder::getInboundTime, LocalDateTime.now().withNano(0));
        }
        orderMapper.update(null, w);
        log.info("确认入库: id={} no={}", id, existing.getInboundNo());
    }

    /** 待入库 / 已入库 → 已作废 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        currentUserService.requireManager("作废入库单");
        InboundOrder existing = getExisting(id);
        if (InboundStatus.CANCELLED.equals(existing.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该入库单已经作废，无需重复操作");
        }

        // 同上：只改状态列，避免把其他可空字段顺手清成 NULL
        orderMapper.update(null, Wrappers.<InboundOrder>lambdaUpdate()
                .eq(InboundOrder::getId, id)
                .set(InboundOrder::getStatus, InboundStatus.CANCELLED));
        log.info("作废入库单: id={} no={}", id, existing.getInboundNo());
    }

    /** 字典：入库类型 + 状态，供前端下拉框使用 */
    public Map<String, Object> dicts() {
        Map<String, Object> data = new HashMap<>();
        data.put("types", toDictList(InboundType.dict()));
        data.put("statuses", toDictList(InboundStatus.dict()));
        return data;
    }

    /* ================= 内部方法 ================= */

    private static List<Map<String, String>> toDictList(Map<String, String> source) {
        List<Map<String, String>> list = new ArrayList<>();
        source.forEach((code, label) -> {
            Map<String, String> item = new HashMap<>();
            item.put("code", code);
            item.put("label", label);
            list.add(item);
        });
        return list;
    }

    /** 按手办名称模糊匹配出相关入库单 id，供关键字查询使用 */
    private Set<Long> findOrderIdsByProductName(String keyword) {
        List<InboundItem> items = itemMapper.selectList(Wrappers.<InboundItem>lambdaQuery()
                .select(InboundItem::getInboundId)
                .like(InboundItem::getProductName, keyword));
        return items.stream()
                .map(InboundItem::getInboundId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 校验并转成待落库的明细：手办必须存在、同一单内不能重复、金额由后端算。
     */
    private List<InboundItem> buildItems(List<InboundItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请至少添加一条手办明细");
        }

        Set<Long> productIds = requests.stream()
                .map(InboundItemRequest::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (productIds.size() != requests.size()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "同一张入库单里同一款手办只能出现一次");
        }

        Map<Long, FigureProduct> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(FigureProduct::getId, Function.identity()));

        List<InboundItem> items = new ArrayList<>(requests.size());
        int sortNo = 10;
        for (InboundItemRequest r : requests) {
            FigureProduct p = products.get(r.getProductId());
            if (p == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND,
                        "手办不存在或已被删除，请重新选择（productId=" + r.getProductId() + "）");
            }

            InboundItem item = new InboundItem();
            item.setProductId(p.getId());
            // 快照：之后手办改名 / 下架，这张单仍显示入库当时的名称与货号
            item.setProductName(p.getName());
            item.setProductCode(p.getCode());
            item.setQuantity(r.getQuantity());
            item.setUnitPrice(r.getUnitPrice() == null
                    ? null
                    : r.getUnitPrice().setScale(2, RoundingMode.HALF_UP));
            item.setAmount(item.getUnitPrice() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : item.getUnitPrice().multiply(BigDecimal.valueOf(r.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));
            item.setRemark(trimToNull(r.getRemark()));
            item.setSortNo(sortNo);
            sortNo += 10;
            items.add(item);
        }
        return items;
    }

    /** 合计统一按明细重算，避免前端传的合计与明细不一致 */
    private void fillTotals(InboundOrder order, List<InboundItem> items) {
        int quantity = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (InboundItem item : items) {
            quantity += item.getQuantity() == null ? 0 : item.getQuantity();
            amount = amount.add(item.getAmount() == null ? BigDecimal.ZERO : item.getAmount());
        }
        order.setTotalQuantity(quantity);
        order.setTotalAmount(amount.setScale(2, RoundingMode.HALF_UP));
    }

    private void saveItems(Long inboundId, List<InboundItem> items) {
        for (InboundItem item : items) {
            item.setId(null);
            item.setInboundId(inboundId);
            itemMapper.insert(item);
        }
    }

    private List<InboundItem> listItems(Long inboundId) {
        return itemMapper.selectList(Wrappers.<InboundItem>lambdaQuery()
                .eq(InboundItem::getInboundId, inboundId)
                .orderByAsc(InboundItem::getSortNo)
                .orderByAsc(InboundItem::getId));
    }

    /** 一页单据的明细一次查回来，在内存里按 inboundId 分组，避免逐行查库 */
    private Map<Long, List<InboundItem>> loadItems(List<InboundOrder> orders) {
        Set<Long> ids = orders.stream().map(InboundOrder::getId).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<InboundItem> items = itemMapper.selectList(Wrappers.<InboundItem>lambdaQuery()
                .in(InboundItem::getInboundId, ids)
                .orderByAsc(InboundItem::getSortNo)
                .orderByAsc(InboundItem::getId));
        Map<Long, List<InboundItem>> grouped = new HashMap<>();
        for (InboundItem item : items) {
            grouped.computeIfAbsent(item.getInboundId(), k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private Map<Long, Warehouse> loadWarehouses(List<InboundOrder> orders) {
        Set<Long> ids = orders.stream().map(InboundOrder::getWarehouseId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
    }

    private Map<Long, Area> loadAreas(List<InboundOrder> orders) {
        Set<Long> ids = orders.stream().map(InboundOrder::getAreaId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return areaMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Area::getId, Function.identity()));
    }

    private Map<Long, Shelf> loadShelves(List<InboundOrder> orders) {
        Set<Long> ids = orders.stream().map(InboundOrder::getShelfId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return shelfMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Shelf::getId, Function.identity()));
    }

    /** 自动单号：RK + yyyyMMdd + 4 位序号，同一天从 0001 递增，撞号就往后找 */
    private String generateInboundNo() {
        String prefix = "RK" + LocalDate.now().format(NO_DATE);
        for (int seq = 1; seq <= 9999; seq++) {
            String candidate = prefix + String.format("%04d", seq);
            if (orderMapper.selectCount(Wrappers.<InboundOrder>lambdaQuery()
                    .eq(InboundOrder::getInboundNo, candidate)) == 0) {
                return candidate;
            }
        }
        throw new BusinessException(ResultCode.BUSINESS_ERROR, "当日入库单号已用尽，请手动填写单号");
    }

    private void checkNoUnique(String inboundNo, Long excludeId) {
        LambdaQueryWrapper<InboundOrder> w = Wrappers.<InboundOrder>lambdaQuery()
                .eq(InboundOrder::getInboundNo, inboundNo);
        if (excludeId != null) {
            w.ne(InboundOrder::getId, excludeId);
        }
        w.last("LIMIT 1");
        if (orderMapper.selectOne(w) != null) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "入库单号已存在：" + inboundNo);
        }
    }

    private void requireWarehouse(Long warehouseId) {
        if (warehouseMapper.selectById(warehouseId) == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "入库仓库不存在或已被删除");
        }
    }

    /** 区域可空；填了就必须存在且与仓库一致，否则单据会指到一个查不到的 / 别的仓库的区域 */
    private void requireArea(Long areaId, Long warehouseId) {
        if (areaId == null) {
            return;
        }
        Area area = areaMapper.selectById(areaId);
        if (area == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "入库区域不存在或已被删除");
        }
        if (!Objects.equals(area.getWarehouseId(), warehouseId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "所选区域不属于该仓库，请重新选择");
        }
    }

    private void requireShelf(Long shelfId, Long warehouseId) {
        if (shelfId == null) {
            return;
        }
        Shelf shelf = shelfMapper.selectById(shelfId);
        if (shelf == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "入库货架不存在或已被删除");
        }
        if (!Objects.equals(shelf.getWarehouseId(), warehouseId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "所选货架不属于该仓库，请重新选择");
        }
    }

    private InboundOrder getExisting(Long id) {
        InboundOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "入库单不存在或已被删除");
        }
        return order;
    }

    private void requireDraft(InboundOrder order, String action) {
        if (!InboundStatus.DRAFT.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "只有「待入库」的单据可以" + action + "，当前状态：" + InboundStatus.labelOf(order.getStatus()));
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
