package com.figureerp.module.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.common.result.PageResult;
import com.figureerp.module.product.entity.FigureProduct;
import com.figureerp.module.product.mapper.FigureProductMapper;
import com.figureerp.module.stock.converter.StockConverter;
import com.figureerp.module.stock.dto.StockQuery;
import com.figureerp.module.stock.entity.InboundItem;
import com.figureerp.module.stock.entity.InboundOrder;
import com.figureerp.module.stock.entity.Shelf;
import com.figureerp.module.stock.entity.Stock;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.mapper.ShelfMapper;
import com.figureerp.module.stock.mapper.StockMapper;
import com.figureerp.module.stock.mapper.WarehouseMapper;
import com.figureerp.module.stock.vo.StockLocationVO;
import com.figureerp.module.stock.vo.StockSummaryVO;
import com.figureerp.module.stock.vo.StockVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存查询 + 由入库单驱动的库存变动。
 * <p>
 * 业务规则：
 * <ul>
 *   <li>库存只由入库单「确认入库」累加、已入库单「作废」回退，不提供手工增删改；</li>
 *   <li>同一手办 + 同一仓库 + 同一区域 + 同一货架 只有一行，再次入库时在原行上累加；</li>
 *   <li>库存行只存「数量 + 成本总额」，均价由 总额 / 数量 算出；作废回退按明细单价冲销，可与累加完全对冲；</li>
 *   <li>列表按手办聚合（SUM 数量 / 金额），点开看库位明细；</li>
 *   <li>查询对所有登录用户开放，库存变动只由入库流程内部调用，不对外暴露接口。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    /** 聚合列别名，和 StockConverter 里的取值保持一致 */
    private static final String COL_PRODUCT_ID = "product_id AS productId";
    private static final String COL_PRODUCT_NAME = "MAX(product_name) AS productName";
    private static final String COL_PRODUCT_CODE = "MAX(product_code) AS productCode";
    private static final String COL_BRAND = "MAX(brand) AS brand";
    private static final String COL_QUANTITY = "SUM(quantity) AS totalQuantity";
    private static final String COL_AMOUNT = "SUM(total_cost) AS totalAmount";
    private static final String COL_LOCATION_COUNT = "COUNT(*) AS locationCount";
    private static final String COL_LAST_INBOUND = "MAX(last_inbound_time) AS lastInboundTime";

    private final StockMapper stockMapper;
    private final FigureProductMapper productMapper;
    private final WarehouseMapper warehouseMapper;
    private final ShelfMapper shelfMapper;

    /* ================= 查询 ================= */

    /**
     * 按手办聚合分页。
     * <p>
     * 聚合用 SQL groupBy（selectMaps），排序与分页在内存里做：
     * 排序键是聚合后的别名，直接交给 SQL 也能跑，但换成「名称升序」这类需要关联商品的排序时
     * 两种写法混用容易出错，这里统一收在 Java 里，逻辑更直观。
     * 在库款式数量级是「几百」级别，一次取回聚合行没有压力。
     */
    public PageResult<StockVO> page(StockQuery q) {
        int page = q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();
        int size = q.getSize() == null || q.getSize() < 1 ? 10 : q.getSize();

        QueryWrapper<Stock> w = new QueryWrapper<>();
        w.select(COL_PRODUCT_ID, COL_PRODUCT_NAME, COL_PRODUCT_CODE, COL_BRAND,
                        COL_QUANTITY, COL_AMOUNT, COL_LOCATION_COUNT, COL_LAST_INBOUND)
                .gt("quantity", 0)
                .groupBy("product_id");
        applyFilter(w, q);

        List<Map<String, Object>> rows = stockMapper.selectMaps(w);
        long total = rows.size();
        if (total == 0) {
            return PageResult.empty(page, size);
        }

        // SQL 聚合出来的都是 Object，这里先算出数量 / 金额用于排序
        rows.sort(comparatorFor(q.getSortBy()));

        int from = Math.min((page - 1) * size, rows.size());
        int to = Math.min(from + size, rows.size());
        List<Map<String, Object>> pageRows = rows.subList(from, to);

        Map<Long, FigureProduct> products = loadProducts(pageRows);
        List<StockVO> records = pageRows.stream()
                .map(r -> StockConverter.toVO(r, products.get(toLong(r.get("productId")))))
                .toList();

        long pages = (total + size - 1) / size;
        return new PageResult<>(records, total, page, size, pages);
    }

    /** 顶部统计卡：在库款式 / 件数 / 金额 / 涉及仓库 / 占用库位 */
    public StockSummaryVO summary() {
        QueryWrapper<Stock> w = new QueryWrapper<>();
        w.select("COUNT(DISTINCT product_id) AS skuCount",
                        "COALESCE(SUM(quantity), 0) AS totalQuantity",
                        "COALESCE(SUM(total_cost), 0) AS totalAmount",
                        "COUNT(DISTINCT warehouse_id) AS warehouseCount",
                        "COUNT(*) AS locationCount")
                .gt("quantity", 0);

        List<Map<String, Object>> list = stockMapper.selectMaps(w);
        StockSummaryVO vo = new StockSummaryVO();
        Map<String, Object> row = list.isEmpty() ? Map.of() : list.get(0);
        vo.setSkuCount(toLong(row.get("skuCount")));
        vo.setTotalQuantity(toLong(row.get("totalQuantity")));
        vo.setTotalAmount(toDecimal(row.get("totalAmount")));
        vo.setWarehouseCount(toLong(row.get("warehouseCount")));
        vo.setLocationCount(toLong(row.get("locationCount")));
        return vo;
    }

    /** 某款手办的库位明细：哪个仓库 / 货架 各有多少件 */
    public List<StockLocationVO> locations(Long productId) {
        List<Stock> rows = stockMapper.selectList(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getProductId, productId)
                .gt(Stock::getQuantity, 0)
                .orderByAsc(Stock::getWarehouseId)
                .orderByAsc(Stock::getShelfId)
                .orderByAsc(Stock::getId));
        if (rows.isEmpty()) {
            return List.of();
        }

        // 仓库 / 货架 各查一次，避免逐行查库
        Map<Long, Warehouse> warehouses = warehouseMapper.selectBatchIds(
                        collect(rows, Stock::getWarehouseId)).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Set<Long> shelfIds = collect(rows, Stock::getShelfId);
        Map<Long, Shelf> shelves = shelfIds.isEmpty() ? Map.of()
                : shelfMapper.selectBatchIds(shelfIds).stream()
                .collect(Collectors.toMap(Shelf::getId, Function.identity()));

        List<StockLocationVO> list = new ArrayList<>(rows.size());
        for (Stock s : rows) {
            // 可空外键：先判 null 再 get，Map.of() 拿 null 当 key 会抛 NPE
            list.add(StockConverter.toLocationVO(s,
                    warehouses.get(s.getWarehouseId()),
                    s.getShelfId() == null ? null : shelves.get(s.getShelfId())));
        }
        return list;
    }

    /* ================= 入库 / 作废 引起的库存变动 ================= */

    /**
     * 确认入库：按明细把数量累加到对应库位。
     * <p>
     * 由 {@code InboundService.confirm} 在同一事务内调用，失败一起回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyInbound(InboundOrder order, List<InboundItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (InboundItem item : items) {
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            if (qty <= 0) {
                continue;
            }
            Stock existing = findRow(item.getProductId(), order.getWarehouseId(), order.getShelfId());

            if (existing == null) {
                Stock stock = new Stock();
                stock.setProductId(item.getProductId());
                stock.setProductName(item.getProductName());
                stock.setProductCode(item.getProductCode());
                stock.setBrand(lookupBrand(item.getProductId()));
                stock.setWarehouseId(order.getWarehouseId());
                stock.setShelfId(order.getShelfId());
                stock.setQuantity(qty);
                stock.setTotalCost(costOf(item, qty, 0, BigDecimal.ZERO));
                stock.setLastInboundTime(order.getInboundTime());
                stockMapper.insert(stock);
                log.info("新增库存行: product={} 仓库={} 货架={} 数量={} 成本={}",
                        item.getProductId(), order.getWarehouseId(), order.getShelfId(),
                        stock.getQuantity(), stock.getTotalCost());
            } else {
                int oldQty = existing.getQuantity() == null ? 0 : existing.getQuantity();
                BigDecimal oldCost = existing.getTotalCost() == null ? BigDecimal.ZERO : existing.getTotalCost();
                BigDecimal newCost = oldCost.add(costOf(item, qty, oldQty, oldCost));

                // 用 update wrapper 显式写每一列：商品改名后快照也要跟着刷新，
                // 而 updateById 会跳过 null，清不干净（这块踩过 FieldStrategy 的坑）
                stockMapper.update(null, Wrappers.<Stock>lambdaUpdate()
                        .eq(Stock::getId, existing.getId())
                        .set(Stock::getQuantity, oldQty + qty)
                        .set(Stock::getTotalCost, newCost)
                        .set(Stock::getLastInboundTime, order.getInboundTime())
                        .set(Stock::getProductName, item.getProductName())
                        .set(Stock::getProductCode, item.getProductCode())
                        .set(Stock::getBrand, lookupBrand(item.getProductId())));
                log.info("累加库存: stockId={} 数量 {} → {} 成本 {} → {}",
                        existing.getId(), oldQty, oldQty + qty, oldCost, newCost);
            }
        }
    }

    /**
     * 已入库单作废：按明细把数量退回。数量减到 0 的行直接逻辑删除，避免留下 0 件的空行。
     * <p>
     * 由 {@code InboundService.cancel} 在「原状态是已入库」时调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revertInbound(InboundOrder order, List<InboundItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (InboundItem item : items) {
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            if (qty <= 0) {
                continue;
            }
            Stock existing = findRow(item.getProductId(), order.getWarehouseId(), order.getShelfId());
            if (existing == null) {
                log.warn("作废回退库存时找不到库存行，跳过: product={} 仓库={} 货架={}",
                        item.getProductId(), order.getWarehouseId(), order.getShelfId());
                continue;
            }

            int oldQty = existing.getQuantity() == null ? 0 : existing.getQuantity();
            BigDecimal oldCost = existing.getTotalCost() == null ? BigDecimal.ZERO : existing.getTotalCost();
            int remain = oldQty - qty;
            if (remain <= 0) {
                // 数量归零：整行删掉（逻辑删除），列表不再展示 0 件的空库存
                stockMapper.deleteById(existing.getId());
                log.info("库存归零删除: stockId={} 原数量={} 回退={}", existing.getId(), oldQty, qty);
            } else {
                // 按入库明细的单价冲销，和当初累加的是同一笔钱
                BigDecimal newCost = oldCost.subtract(costOf(item, qty, oldQty, oldCost))
                        .max(BigDecimal.ZERO);
                stockMapper.update(null, Wrappers.<Stock>lambdaUpdate()
                        .eq(Stock::getId, existing.getId())
                        .set(Stock::getQuantity, remain)
                        .set(Stock::getTotalCost, newCost));
                log.info("回退库存: stockId={} 数量 {} → {} 成本 {} → {}",
                        existing.getId(), oldQty, remain, oldCost, newCost);
            }
        }
    }

    /* ================= 内部方法 ================= */

    private void applyFilter(QueryWrapper<Stock> w, StockQuery q) {
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            w.and(x -> x.like("product_name", kw)
                    .or().like("product_code", kw)
                    .or().like("brand", kw));
        }
        if (q.getWarehouseId() != null) {
            w.eq("warehouse_id", q.getWarehouseId());
        }
    }

    private Comparator<Map<String, Object>> comparatorFor(String sortBy) {
        String sort = sortBy == null ? "" : sortBy.trim().toUpperCase();
        return switch (sort) {
            case "AMOUNT" -> Comparator
                    .comparing((Map<String, Object> r) -> toDecimal(r.get("totalAmount")))
                    .reversed();
            case "RECENT" -> Comparator
                    .comparing((Map<String, Object> r) -> toDateTime(r.get("lastInboundTime")),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed()
                    .thenComparing(r -> toDecimal(r.get("totalAmount")), Comparator.reverseOrder());
            case "NAME" -> Comparator.comparing(r -> String.valueOf(r.get("productName")));
            // 默认按件数降序：一眼看到库存最多的货
            default -> Comparator
                    .comparing((Map<String, Object> r) -> toDecimal(r.get("totalQuantity")))
                    .reversed();
        };
    }

    /** 同一手办 + 同一仓库 + 同一货架 的库存行 */
    private Stock findRow(Long productId, Long warehouseId, Long shelfId) {
        LambdaQueryWrapper<Stock> w = Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getProductId, productId)
                .eq(Stock::getWarehouseId, warehouseId);
        // 可空列要用 isNull 判断，不能 eq(null)（会拼成 = NULL，永远不成立）
        if (shelfId == null) {
            w.isNull(Stock::getShelfId);
        } else {
            w.eq(Stock::getShelfId, shelfId);
        }
        return stockMapper.selectOne(w.last("LIMIT 1"));
    }

    /** 品牌快照取自商品档案（商品已被删时返回 null，前端展示占位） */
    private String lookupBrand(Long productId) {
        if (productId == null) {
            return null;
        }
        FigureProduct p = productMapper.selectById(productId);
        return p == null ? null : p.getBrand();
    }

    /**
     * 本次变动要计入的成本（正数）。
     * <p>
     * 明细有单价就按单价算；没填单价时按现有均价折算，否则「加了数量却不加成本」
     * 会把均价冲低，库存金额凭空缩水。现有数量也是 0 时只能记 0。
     */
    private BigDecimal costOf(InboundItem item, int qty, int oldQty, BigDecimal oldCost) {
        if (item.getUnitPrice() != null) {
            return item.getUnitPrice().multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        if (oldQty > 0 && oldCost.compareTo(BigDecimal.ZERO) > 0) {
            return oldCost.multiply(BigDecimal.valueOf(qty))
                    .divide(BigDecimal.valueOf(oldQty), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private Map<Long, FigureProduct> loadProducts(List<Map<String, Object>> rows) {
        Set<Long> ids = rows.stream()
                .map(r -> toLong(r.get("productId")))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        // 注意：逻辑删除的商品查不出来（这是想要的效果）——查不到即视为已下架
        return productMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(FigureProduct::getId, Function.identity()));
    }

    private static <T> Set<Long> collect(List<Stock> rows, Function<Stock, Long> getter) {
        return rows.stream().map(getter).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        return v instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(v));
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return v instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(v));
    }

    private static java.time.LocalDateTime toDateTime(Object v) {
        if (v instanceof java.time.LocalDateTime dt) {
            return dt;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }
}
