package com.figureerp.module.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.stock.converter.WarehouseConverter;
import com.figureerp.module.stock.converter.WarehouseStatus;
import com.figureerp.module.stock.dto.WarehouseQuery;
import com.figureerp.module.stock.dto.WarehouseSaveRequest;
import com.figureerp.module.stock.entity.Area;
import com.figureerp.module.stock.entity.Shelf;
import com.figureerp.module.stock.entity.Warehouse;
import com.figureerp.module.stock.mapper.AreaMapper;
import com.figureerp.module.stock.mapper.ShelfMapper;
import com.figureerp.module.stock.mapper.WarehouseMapper;
import com.figureerp.module.stock.vo.WarehouseVO;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 仓库档案的查询与维护。
 * <p>
 * 业务规则：
 * <ul>
 *   <li>仓库代码在「未删除记录」中必须唯一；</li>
 *   <li>状态只能是 A/C/D，类型只能是 N/Y；</li>
 *   <li>查询所有登录用户可用，增删改需要管理员或主管。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseMapper mapper;
    private final ShelfMapper shelfMapper;
    private final AreaMapper areaMapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<WarehouseVO> page(WarehouseQuery q) {
        LambdaQueryWrapper<Warehouse> w = Wrappers.lambdaQuery();

        if (StringUtils.hasText(q.getWarehouseCode())) {
            w.like(Warehouse::getWarehouseCode, q.getWarehouseCode().trim());
        }
        if (StringUtils.hasText(q.getWarehouseName())) {
            w.like(Warehouse::getWarehouseName, q.getWarehouseName().trim());
        }
        if (StringUtils.hasText(q.getIsReturn())) {
            w.eq(Warehouse::getIsReturn, q.getIsReturn());
        }
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(Warehouse::getStatus, q.getStatus());
        }

        // 按排序号升序，同序时按 id 升序，保证多次查询顺序稳定
        w.orderByAsc(Warehouse::getSortNo).orderByAsc(Warehouse::getId);

        Page<Warehouse> page = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return PageResult.of(page, WarehouseConverter::toVO);
    }

    public WarehouseVO detail(Long id) {
        return WarehouseConverter.toVO(getExisting(id));
    }

    /* ================= 维护 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(WarehouseSaveRequest req) {
        currentUserService.requireManager("维护仓库");
        checkCodeUnique(req.getWarehouseCode(), null);

        Warehouse w = new Warehouse();
        apply(w, req, null);
        mapper.insert(w);
        log.info("新增仓库: id={} code={} name={}", w.getId(), w.getWarehouseCode(), w.getWarehouseName());
        return w.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, WarehouseSaveRequest req) {
        currentUserService.requireManager("维护仓库");
        Warehouse existing = getExisting(id);
        checkCodeUnique(req.getWarehouseCode(), id);

        Warehouse w = new Warehouse();
        w.setId(id);
        apply(w, req, existing);
        mapper.updateById(w);
        log.info("编辑仓库: id={} code={} name={}", id, w.getWarehouseCode(), w.getWarehouseName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        currentUserService.requireManager("删除仓库");
        Warehouse existing = getExisting(id);

        // 仓库下还有货架时不允许删，否则货架会指向一个查不到的仓库
        Long shelfCount = shelfMapper.selectCount(Wrappers.<Shelf>lambdaQuery()
                .eq(Shelf::getWarehouseId, id));
        if (shelfCount != null && shelfCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "仓库「" + existing.getWarehouseName() + "」下还有 " + shelfCount + " 个货架，请先删除货架");
        }

        // 仓库下还有区域时同样不允许删，否则区域会指向一个查不到的仓库
        Long areaCount = areaMapper.selectCount(Wrappers.<Area>lambdaQuery()
                .eq(Area::getWarehouseId, id));
        if (areaCount != null && areaCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "仓库「" + existing.getWarehouseName() + "」下还有 " + areaCount + " 个区域，请先删除区域");
        }

        mapper.deleteById(id);
        log.info("删除仓库: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        currentUserService.requireManager("修改仓库状态");
        if (!WarehouseStatus.isValidStatus(status)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "仓库状态只能是 A、C 或 D");
        }
        getExisting(id);

        Warehouse w = new Warehouse();
        w.setId(id);
        w.setStatus(status);
        mapper.updateById(w);
    }

    /* ================= 内部方法 ================= */

    private void apply(Warehouse w, WarehouseSaveRequest req, Warehouse existing) {
        w.setWarehouseCode(req.getWarehouseCode().trim());
        w.setWarehouseName(req.getWarehouseName().trim());
        w.setWarehouseAddress(blankToNull(req.getWarehouseAddress()));
        w.setIsReturn(StringUtils.hasText(req.getIsReturn()) ? req.getIsReturn() : WarehouseStatus.NORMAL);
        w.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : WarehouseStatus.IN_USE);
        w.setRemark(blankToNull(req.getRemark()));

        // 编辑时前端可能没传排序号，保持原值而不是清零
        if (req.getSortNo() != null) {
            w.setSortNo(req.getSortNo());
        } else {
            w.setSortNo(existing != null && existing.getSortNo() != null ? existing.getSortNo() : 0);
        }
    }

    /** 未删除记录中仓库代码不能重复（表上没建 UNIQUE，见 schema.sql 的说明） */
    private void checkCodeUnique(String code, Long excludeId) {
        String trimmed = code.trim();
        LambdaQueryWrapper<Warehouse> w = Wrappers.<Warehouse>lambdaQuery()
                .eq(Warehouse::getWarehouseCode, trimmed);
        if (excludeId != null) {
            w.ne(Warehouse::getId, excludeId);
        }
        w.last("LIMIT 1");
        if (mapper.selectOne(w) != null) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "仓库代码已存在：" + trimmed);
        }
    }

    private Warehouse getExisting(Long id) {
        Warehouse w = mapper.selectById(id);
        if (w == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "仓库不存在或已被删除");
        }
        return w;
    }

    private static String blankToNull(String value) {
        return value == null ? null : value.trim();
    }
}
