package com.figureerp.module.manufacturer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.manufacturer.converter.ManufacturerConverter;
import com.figureerp.module.manufacturer.dto.ManufacturerQuery;
import com.figureerp.module.manufacturer.dto.ManufacturerSaveRequest;
import com.figureerp.module.manufacturer.entity.Manufacturer;
import com.figureerp.module.manufacturer.mapper.ManufacturerMapper;
import com.figureerp.module.manufacturer.vo.ManufacturerOptionVO;
import com.figureerp.module.manufacturer.vo.ManufacturerSummaryVO;
import com.figureerp.module.manufacturer.vo.ManufacturerVO;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 厂商目录的查询与维护。
 * <p>
 * 业务规则：
 * <ul>
 *   <li>公司名称在未删除记录内唯一，避免下拉选项里出现两个「万代」；</li>
 *   <li>{@code status} 只能是 0 / 1；停用后不出现在下拉选项，但列表仍可查到；</li>
 *   <li>查询 / 下拉选项对所有登录用户开放，增删改与启停需要管理员或主管。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManufacturerService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final ManufacturerMapper manufacturerMapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<ManufacturerVO> page(ManufacturerQuery q) {
        LambdaQueryWrapper<Manufacturer> w = Wrappers.lambdaQuery();

        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            w.and(x -> x.like(Manufacturer::getName, kw).or().like(Manufacturer::getCountry, kw));
        }
        if (StringUtils.hasText(q.getCountry())) {
            w.eq(Manufacturer::getCountry, q.getCountry().trim());
        }
        if (q.getStatus() != null) {
            w.eq(Manufacturer::getStatus, q.getStatus());
        }

        applySort(w, q.getSortBy());

        Page<Manufacturer> page = manufacturerMapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return PageResult.of(page, ManufacturerConverter::toVO);
    }

    /** 已录入厂商出现过的国家地区（去重排序），供筛选下拉使用 */
    public List<String> countries() {
        return manufacturerMapper.selectList(Wrappers.<Manufacturer>lambdaQuery()
                        .select(Manufacturer::getCountry)
                        .isNotNull(Manufacturer::getCountry))
                .stream()
                .map(Manufacturer::getCountry)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    public ManufacturerVO detail(Long id) {
        return ManufacturerConverter.toVO(getExisting(id));
    }

    /** 列表页顶部统计：总数 / 启用 / 停用 / 覆盖国家地区数 */
    public ManufacturerSummaryVO summary() {
        Long t = manufacturerMapper.selectCount(null);
        long total = t == null ? 0 : t;
        Long e = manufacturerMapper.selectCount(Wrappers.<Manufacturer>lambdaQuery()
                .eq(Manufacturer::getStatus, STATUS_ENABLED));
        long enabled = e == null ? 0 : e;
        return new ManufacturerSummaryVO(total, enabled, total - enabled, countries().size());
    }

    /**
     * 其他业务表选择厂商时的下拉选项，按名称排序。
     *
     * @param keyword         名称模糊关键字，可空
     * @param includeDisabled 是否包含停用厂商：录入表单传 false（只选启用的），
     *                        列表筛选传 true（历史数据可能挂在已停用的厂商上）
     */
    public List<ManufacturerOptionVO> options(String keyword, boolean includeDisabled) {
        LambdaQueryWrapper<Manufacturer> w = Wrappers.<Manufacturer>lambdaQuery()
                .orderByAsc(Manufacturer::getName)
                .last("LIMIT 200");
        if (!includeDisabled) {
            w.eq(Manufacturer::getStatus, STATUS_ENABLED);
        }
        if (StringUtils.hasText(keyword)) {
            w.like(Manufacturer::getName, keyword.trim());
        }
        return manufacturerMapper.selectList(w).stream()
                .map(e -> new ManufacturerOptionVO(e.getId(), e.getName(), e.getLogo(), e.getCountry()))
                .toList();
    }

    /* ================= 维护 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(ManufacturerSaveRequest req) {
        currentUserService.requireManager("维护厂商目录");
        validate(req);
        checkNameUnique(req.getName(), null);

        Manufacturer m = new Manufacturer();
        m.setName(req.getName().trim());
        m.setLogo(blankToNull(req.getLogo()));
        m.setFoundedDate(req.getFoundedDate());
        m.setCountry(blankToNull(req.getCountry()));
        m.setRemark(blankToNull(req.getRemark()));
        m.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
        manufacturerMapper.insert(m);

        log.info("新增厂商: id={} name={}", m.getId(), m.getName());
        return m.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ManufacturerSaveRequest req) {
        currentUserService.requireManager("维护厂商目录");
        validate(req);
        Manufacturer existing = getExisting(id);
        checkNameUnique(req.getName(), id);

        // 用 UpdateWrapper 显式 set 每一列（含 null），这样用户把 Logo / 国家 / 备注清空后能真正落库；
        // updateById 默认跳过 null，会把「清空」当成「不修改」。
        manufacturerMapper.update(null, Wrappers.<Manufacturer>lambdaUpdate()
                .eq(Manufacturer::getId, id)
                .set(Manufacturer::getName, req.getName().trim())
                .set(Manufacturer::getLogo, blankToNull(req.getLogo()))
                .set(Manufacturer::getFoundedDate, req.getFoundedDate())
                .set(Manufacturer::getCountry, blankToNull(req.getCountry()))
                .set(Manufacturer::getRemark, blankToNull(req.getRemark()))
                .set(Manufacturer::getStatus, req.getStatus() == null ? existing.getStatus() : req.getStatus())
                .set(Manufacturer::getUpdateTime, LocalDateTime.now()));

        log.info("编辑厂商: id={} name={}", id, req.getName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        currentUserService.requireManager("删除厂商");
        getExisting(id);
        manufacturerMapper.deleteById(id);
        log.info("删除厂商: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        currentUserService.requireManager("修改厂商状态");
        if (!Integer.valueOf(STATUS_ENABLED).equals(status) && !Integer.valueOf(STATUS_DISABLED).equals(status)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "状态值只能是 0 或 1");
        }
        getExisting(id);
        // 只更新状态列，避免整实体更新误改其他字段
        manufacturerMapper.update(null, Wrappers.<Manufacturer>lambdaUpdate()
                .eq(Manufacturer::getId, id)
                .set(Manufacturer::getStatus, status)
                .set(Manufacturer::getUpdateTime, LocalDateTime.now()));
        log.info("厂商状态变更: id={} status={}", id, status);
    }

    /* ================= 内部方法 ================= */

    private void applySort(LambdaQueryWrapper<Manufacturer> w, String sortBy) {
        switch (sortBy == null ? "createDesc" : sortBy) {
            case "nameAsc" -> w.orderByAsc(Manufacturer::getName).orderByDesc(Manufacturer::getId);
            case "foundedAsc" -> w.orderByAsc(Manufacturer::getFoundedDate).orderByDesc(Manufacturer::getId);
            case "foundedDesc" -> w.orderByDesc(Manufacturer::getFoundedDate).orderByDesc(Manufacturer::getId);
            // 默认按创建时间倒序：刚录入的厂商通常就是要接着补充资料的那条
            default -> w.orderByDesc(Manufacturer::getCreateTime).orderByDesc(Manufacturer::getId);
        }
    }

    private void validate(ManufacturerSaveRequest req) {
        if (req.getStatus() != null && req.getStatus() != STATUS_ENABLED && req.getStatus() != STATUS_DISABLED) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "状态值只能是 0 或 1");
        }
    }

    /** 同名厂商不能重复录入：名称在未删除记录内唯一 */
    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<Manufacturer> w = Wrappers.<Manufacturer>lambdaQuery()
                .eq(Manufacturer::getName, name.trim());
        if (excludeId != null) {
            w.ne(Manufacturer::getId, excludeId);
        }
        w.last("LIMIT 1");
        if (manufacturerMapper.selectOne(w) != null) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "该厂商已存在：" + name.trim());
        }
    }

    private Manufacturer getExisting(Long id) {
        Manufacturer m = manufacturerMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "厂商不存在或已被删除");
        }
        return m;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
