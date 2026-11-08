package com.figureerp.module.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.stock.converter.ShelfConverter;
import com.figureerp.module.stock.converter.ShelfStatus;
import com.figureerp.module.stock.dto.ShelfQuery;
import com.figureerp.module.stock.dto.ShelfSaveRequest;
import com.figureerp.module.stock.entity.Area;
import com.figureerp.module.stock.entity.Shelf;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.mapper.AreaMapper;
import com.figureerp.module.stock.mapper.ShelfMapper;
import com.figureerp.module.stock.mapper.WarehouseMapper;
import com.figureerp.module.stock.vo.ShelfVO;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 货架档案的查询与维护。
 * <p>
 * 业务规则：
 * <ul>
 *   <li>货架必须挂在已存在的仓库下；</li>
 *   <li>货架编号在「同一仓库 + 未删除」范围内唯一（不同仓库可以重号）；</li>
 *   <li>状态只能是 A/B/D；</li>
 *   <li>查询所有登录用户可用，增删改需要管理员或主管。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShelfService {

    private final ShelfMapper mapper;
    private final WarehouseMapper warehouseMapper;
    private final AreaMapper areaMapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<ShelfVO> page(ShelfQuery q) {
        LambdaQueryWrapper<Shelf> w = Wrappers.lambdaQuery();

        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            // 关键字同时匹配货架编号和库位信息，输入 01-1 或 01区 都能查到
            w.and(x -> x.like(Shelf::getShelfCode, kw).or().like(Shelf::getLocation, kw));
        }
        if (q.getWarehouseId() != null) {
            w.eq(Shelf::getWarehouseId, q.getWarehouseId());
        }
        if (q.getAreaId() != null) {
            w.eq(Shelf::getAreaId, q.getAreaId());
        }
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(Shelf::getStatus, q.getStatus());
        }

        // 按排序号升序，同序时按 id 升序，保证多次查询顺序稳定
        w.orderByAsc(Shelf::getSortNo).orderByAsc(Shelf::getId);

        Page<Shelf> page = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);

        // 一次把这页涉及的仓库、区域批量查出来拼装，避免逐行查库
        Map<Long, Warehouse> warehouses = loadWarehouses(page.getRecords());
        Map<Long, Area> areas = loadAreas(page.getRecords());
        // 注意不能直接 areas.get(s.getAreaId())：没有区域时 loadAreas 返回 Map.of()，
        // 不可变 Map 用 null 作 key 查会抛 NPE（货架本来就可以不挂区域）
        return PageResult.of(page, s -> {
            Area area = s.getAreaId() == null ? null : areas.get(s.getAreaId());
            return ShelfConverter.toVO(s, warehouses.get(s.getWarehouseId()), area);
        });
    }

    public ShelfVO detail(Long id) {
        Shelf shelf = getExisting(id);
        Area area = shelf.getAreaId() == null ? null : areaMapper.selectById(shelf.getAreaId());
        return ShelfConverter.toVO(shelf, warehouseMapper.selectById(shelf.getWarehouseId()), area);
    }

    /* ================= 维护 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(ShelfSaveRequest req) {
        currentUserService.requireManager("维护货架");
        requireWarehouse(req.getWarehouseId());
        requireArea(req.getAreaId(), req.getWarehouseId());
        checkCodeUnique(req.getWarehouseId(), req.getShelfCode(), null);

        Shelf shelf = new Shelf();
        apply(shelf, req, null);
        mapper.insert(shelf);
        log.info("新增货架: id={} warehouseId={} code={}", shelf.getId(), shelf.getWarehouseId(), shelf.getShelfCode());
        return shelf.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ShelfSaveRequest req) {
        currentUserService.requireManager("维护货架");
        Shelf existing = getExisting(id);
        requireWarehouse(req.getWarehouseId());
        requireArea(req.getAreaId(), req.getWarehouseId());
        checkCodeUnique(req.getWarehouseId(), req.getShelfCode(), id);

        Shelf shelf = new Shelf();
        shelf.setId(id);
        apply(shelf, req, existing);
        mapper.updateById(shelf);
        log.info("编辑货架: id={} warehouseId={} code={}", id, shelf.getWarehouseId(), shelf.getShelfCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        currentUserService.requireManager("删除货架");
        getExisting(id);
        mapper.deleteById(id);
        log.info("删除货架: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        currentUserService.requireManager("修改货架状态");
        if (!ShelfStatus.isValid(status)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "货架状态只能是 A、B 或 D");
        }
        getExisting(id);

        Shelf shelf = new Shelf();
        shelf.setId(id);
        shelf.setStatus(status);
        mapper.updateById(shelf);
    }

    /** 供仓库删除前做占用校验，返回该仓库下未删除的货架数 */
    public long countByWarehouse(Long warehouseId) {
        return mapper.selectCount(Wrappers.<Shelf>lambdaQuery().eq(Shelf::getWarehouseId, warehouseId));
    }

    /* ================= 内部方法 ================= */

    private void apply(Shelf s, ShelfSaveRequest req, Shelf existing) {
        s.setShelfCode(req.getShelfCode().trim());
        s.setWarehouseId(req.getWarehouseId());
        s.setAreaId(req.getAreaId());
        s.setLocation(blankToNull(req.getLocation()));
        s.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : ShelfStatus.NORMAL);
        s.setRemark(blankToNull(req.getRemark()));

        // 编辑时前端可能没传排序号，保持原值而不是清零
        if (req.getSortNo() != null) {
            s.setSortNo(req.getSortNo());
        } else {
            s.setSortNo(existing != null && existing.getSortNo() != null ? existing.getSortNo() : 0);
        }
    }

    /** 仓库必须存在且未删除，否则货架会指向一个查不到的仓库 */
    private Warehouse requireWarehouse(Long warehouseId) {
        Warehouse w = warehouseMapper.selectById(warehouseId);
        if (w == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "所属仓库不存在或已被删除");
        }
        return w;
    }

    /** 区域可空；填了就必须存在，且必须与货架属于同一仓库，避免「3101 的货架挂在 3301 的区域下」 */
    private void requireArea(Long areaId, Long warehouseId) {
        if (areaId == null) {
            return;
        }
        Area area = areaMapper.selectById(areaId);
        if (area == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "所属区域不存在或已被删除");
        }
        if (!area.getWarehouseId().equals(warehouseId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "所属区域不属于所选仓库，请重新选择");
        }
    }

    /** 同一仓库的未删除记录中货架编号不能重复（表上没建 UNIQUE，见 schema.sql 的说明） */
    private void checkCodeUnique(Long warehouseId, String code, Long excludeId) {
        String trimmed = code.trim();
        LambdaQueryWrapper<Shelf> w = Wrappers.<Shelf>lambdaQuery()
                .eq(Shelf::getWarehouseId, warehouseId)
                .eq(Shelf::getShelfCode, trimmed);
        if (excludeId != null) {
            w.ne(Shelf::getId, excludeId);
        }
        w.last("LIMIT 1");
        if (mapper.selectOne(w) != null) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "该仓库下货架编号已存在：" + trimmed);
        }
    }

    private Shelf getExisting(Long id) {
        Shelf shelf = mapper.selectById(id);
        if (shelf == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "货架不存在或已被删除");
        }
        return shelf;
    }

    private Map<Long, Area> loadAreas(List<Shelf> shelves) {
        Set<Long> ids = shelves.stream()
                .map(Shelf::getAreaId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return areaMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Area::getId, Function.identity()));
    }

    private Map<Long, Warehouse> loadWarehouses(List<Shelf> shelves) {
        Set<Long> ids = shelves.stream()
                .map(Shelf::getWarehouseId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
    }

    private static String blankToNull(String value) {
        return value == null ? null : value.trim();
    }
}
