package com.figureerp.module.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.stock.converter.AreaConverter;
import com.figureerp.module.stock.converter.AreaStatus;
import com.figureerp.module.stock.dto.AreaQuery;
import com.figureerp.module.stock.dto.AreaSaveRequest;
import com.figureerp.module.stock.entity.Area;
import com.figureerp.module.stock.entity.Shelf;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.mapper.AreaMapper;
import com.figureerp.module.stock.mapper.ShelfMapper;
import com.figureerp.module.stock.mapper.WarehouseMapper;
import com.figureerp.module.stock.vo.AreaVO;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 区域档案的查询与维护。
 * <p>
 * 业务规则：
 * <ul>
 *   <li>区域必须挂在已存在的仓库下；</li>
 *   <li>区域编号在「同一仓库 + 未删除」范围内唯一（不同仓库可以重号）；</li>
 *   <li>状态只能是 A/D；</li>
 *   <li>区域下还有货架时不允许删除；</li>
 *   <li>查询所有登录用户可用，增删改需要管理员或主管。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AreaService {

    private final AreaMapper mapper;
    private final WarehouseMapper warehouseMapper;
    private final ShelfMapper shelfMapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<AreaVO> page(AreaQuery q) {
        LambdaQueryWrapper<Area> w = Wrappers.lambdaQuery();

        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            // 关键字同时匹配区域编号和区域名称，输入 A 或 展示柜 都能查到
            w.and(x -> x.like(Area::getAreaCode, kw).or().like(Area::getAreaName, kw));
        }
        if (q.getWarehouseId() != null) {
            w.eq(Area::getWarehouseId, q.getWarehouseId());
        }
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(Area::getStatus, q.getStatus());
        }

        // 按排序号升序，同序时按 id 升序，保证多次查询顺序稳定
        w.orderByAsc(Area::getSortNo).orderByAsc(Area::getId);

        Page<Area> page = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);

        Map<Long, Warehouse> warehouses = loadWarehouses(page.getRecords());
        Map<Long, Long> shelfCounts = loadShelfCounts(page.getRecords());
        return PageResult.of(page, a -> AreaConverter.toVO(a, warehouses.get(a.getWarehouseId()),
                shelfCounts.get(a.getId())));
    }

    public AreaVO detail(Long id) {
        Area area = getExisting(id);
        return AreaConverter.toVO(area, warehouseMapper.selectById(area.getWarehouseId()), countShelf(id));
    }

    /* ================= 维护 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(AreaSaveRequest req) {
        currentUserService.requireManager("维护区域");
        requireWarehouse(req.getWarehouseId());
        checkCodeUnique(req.getWarehouseId(), req.getAreaCode(), null);

        Area area = new Area();
        apply(area, req, null);
        mapper.insert(area);
        log.info("新增区域: id={} warehouseId={} code={}", area.getId(), area.getWarehouseId(), area.getAreaCode());
        return area.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AreaSaveRequest req) {
        currentUserService.requireManager("维护区域");
        Area existing = getExisting(id);
        requireWarehouse(req.getWarehouseId());
        checkCodeUnique(req.getWarehouseId(), req.getAreaCode(), id);

        // 区域下已有货架时不允许换仓库，否则货架的 warehouse_id 会和区域对不上
        if (!Objects.equals(existing.getWarehouseId(), req.getWarehouseId()) && countShelf(id) > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "该区域下还有 " + countShelf(id) + " 个货架，不能改到其他仓库，请先移走货架");
        }

        Area area = new Area();
        area.setId(id);
        apply(area, req, existing);
        mapper.updateById(area);
        log.info("编辑区域: id={} warehouseId={} code={}", id, area.getWarehouseId(), area.getAreaCode());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        currentUserService.requireManager("删除区域");
        Area existing = getExisting(id);

        long shelfCount = countShelf(id);
        if (shelfCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "区域「" + existing.getAreaName() + "」下还有 " + shelfCount + " 个货架，请先删除货架或将货架改到其他区域");
        }

        mapper.deleteById(id);
        log.info("删除区域: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        currentUserService.requireManager("修改区域状态");
        if (!AreaStatus.isValid(status)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "区域状态只能是 A 或 D");
        }
        getExisting(id);

        Area area = new Area();
        area.setId(id);
        area.setStatus(status);
        mapper.updateById(area);
    }

    /** 供仓库删除前做占用校验，返回该仓库下未删除的区域数 */
    public long countByWarehouse(Long warehouseId) {
        return mapper.selectCount(Wrappers.<Area>lambdaQuery().eq(Area::getWarehouseId, warehouseId));
    }

    /* ================= 内部方法 ================= */

    private void apply(Area a, AreaSaveRequest req, Area existing) {
        a.setAreaCode(req.getAreaCode().trim());
        a.setAreaName(req.getAreaName().trim());
        a.setWarehouseId(req.getWarehouseId());
        a.setLocation(blankToNull(req.getLocation()));
        a.setCapacity(req.getCapacity() == null
                ? (existing != null && existing.getCapacity() != null ? existing.getCapacity() : 0)
                : req.getCapacity());
        a.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : AreaStatus.IN_USE);
        a.setRemark(blankToNull(req.getRemark()));

        // 编辑时前端可能没传排序号，保持原值而不是清零
        if (req.getSortNo() != null) {
            a.setSortNo(req.getSortNo());
        } else {
            a.setSortNo(existing != null && existing.getSortNo() != null ? existing.getSortNo() : 0);
        }
    }

    /** 仓库必须存在且未删除，否则区域会指向一个查不到的仓库 */
    private Warehouse requireWarehouse(Long warehouseId) {
        Warehouse w = warehouseMapper.selectById(warehouseId);
        if (w == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "所属仓库不存在或已被删除");
        }
        return w;
    }

    /** 同一仓库的未删除记录中区域编号不能重复（表上没建 UNIQUE，见 schema.sql 的说明） */
    private void checkCodeUnique(Long warehouseId, String code, Long excludeId) {
        String trimmed = code.trim();
        LambdaQueryWrapper<Area> w = Wrappers.<Area>lambdaQuery()
                .eq(Area::getWarehouseId, warehouseId)
                .eq(Area::getAreaCode, trimmed);
        if (excludeId != null) {
            w.ne(Area::getId, excludeId);
        }
        w.last("LIMIT 1");
        if (mapper.selectOne(w) != null) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "该仓库下区域编号已存在：" + trimmed);
        }
    }

    private Area getExisting(Long id) {
        Area area = mapper.selectById(id);
        if (area == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "区域不存在或已被删除");
        }
        return area;
    }

    /** 区域下未删除的货架数 */
    private long countShelf(Long areaId) {
        return shelfMapper.selectCount(Wrappers.<Shelf>lambdaQuery().eq(Shelf::getAreaId, areaId));
    }

    private Map<Long, Warehouse> loadWarehouses(List<Area> areas) {
        Set<Long> ids = areas.stream()
                .map(Area::getWarehouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));
    }

    /** 一次把这页区域下的货架 area_id 查出来在内存里分组计数，避免逐行 selectCount */
    private Map<Long, Long> loadShelfCounts(List<Area> areas) {
        Set<Long> ids = areas.stream()
                .map(Area::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Shelf> shelves = shelfMapper.selectList(Wrappers.<Shelf>lambdaQuery()
                .select(Shelf::getAreaId)
                .in(Shelf::getAreaId, ids));
        Map<Long, Long> counts = new HashMap<>();
        for (Shelf s : shelves) {
            if (s.getAreaId() != null) {
                counts.merge(s.getAreaId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private static String blankToNull(String value) {
        return value == null ? null : value.trim();
    }
}
