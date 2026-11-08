package com.figureerp.module.acg.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.acg.converter.AcgEnums;
import com.figureerp.module.acg.converter.AcgWorkConverter;
import com.figureerp.module.acg.dto.AcgWorkQuery;
import com.figureerp.module.acg.dto.AcgWorkSaveRequest;
import com.figureerp.module.acg.dto.HeatRecordRequest;
import com.figureerp.module.acg.entity.AcgHeatLog;
import com.figureerp.module.acg.entity.AcgWork;
import com.figureerp.module.acg.mapper.AcgHeatLogMapper;
import com.figureerp.module.acg.mapper.AcgWorkMapper;
import com.figureerp.module.acg.vo.AcgHeatPointVO;
import com.figureerp.module.acg.vo.AcgOptionVO;
import com.figureerp.module.acg.vo.AcgWorkVO;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ACG 作品档案的查询与维护。
 * <p>
 * 业务规则：
 * <ul>
 *   <li>同一部作品不能重复录入（名称 + 类型 未删除记录内唯一），避免下拉选项里出现两个「咒术回战」；</li>
 *   <li>作品编号可选，填写后在未删除记录内唯一；</li>
 *   <li>类型、连载状态只能是字典内的值；热度 0~100；</li>
 *   <li>查询 / 下拉选项对所有登录用户开放，增删改与热度录入需要管理员或主管。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcgWorkService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;
    private static final int DEFAULT_TREND_DAYS = 90;
    private static final int MAX_TREND_DAYS = 730;

    private final AcgWorkMapper workMapper;
    private final AcgHeatLogMapper heatLogMapper;
    private final AcgMetricsService metricsService;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<AcgWorkVO> page(AcgWorkQuery q) {
        LambdaQueryWrapper<AcgWork> w = Wrappers.lambdaQuery();

        if (StringUtils.hasText(q.getName())) {
            String keyword = q.getName().trim();
            // 中文名或原名命中都算，输入「フリーレン」也能搜到《葬送的芙莉莲》
            w.and(x -> x.like(AcgWork::getName, keyword).or().like(AcgWork::getOriginalName, keyword));
        }
        if (StringUtils.hasText(q.getWorkCode())) {
            w.like(AcgWork::getWorkCode, q.getWorkCode().trim());
        }
        if (StringUtils.hasText(q.getWorkType())) {
            w.eq(AcgWork::getWorkType, q.getWorkType());
        }
        if (StringUtils.hasText(q.getReleaseStatus())) {
            w.eq(AcgWork::getReleaseStatus, q.getReleaseStatus());
        }
        if (StringUtils.hasText(q.getTag())) {
            w.like(AcgWork::getTags, q.getTag().trim());
        }
        if (q.getReleaseYear() != null) {
            w.eq(AcgWork::getReleaseYear, q.getReleaseYear());
        }
        if (q.getMinHeat() != null) {
            w.ge(AcgWork::getHeatScore, q.getMinHeat());
        }
        if (q.getMaxHeat() != null) {
            w.le(AcgWork::getHeatScore, q.getMaxHeat());
        }
        if (q.getStatus() != null) {
            w.eq(AcgWork::getStatus, q.getStatus());
        }

        applySort(w, q.getSortBy());

        Page<AcgWork> page = workMapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);

        int maxSales = metricsService.maxMonthlySales();
        Map<Long, Integer> prevHeat = metricsService.prevHeat(page.getRecords().stream().map(AcgWork::getId).toList());

        return PageResult.of(page, e -> AcgWorkConverter.toVO(e, maxSales, prevHeat.get(e.getId())));
    }

    public AcgWorkVO detail(Long id) {
        AcgWork work = getExisting(id);
        int maxSales = metricsService.maxMonthlySales();
        Map<Long, Integer> prevHeat = metricsService.prevHeat(List.of(id));
        return AcgWorkConverter.toVO(work, maxSales, prevHeat.get(id));
    }

    /** 其他业务表选择作品时的下拉选项，只返回启用的，按热度倒序 */
    public List<AcgOptionVO> options(String keyword) {
        LambdaQueryWrapper<AcgWork> w = Wrappers.<AcgWork>lambdaQuery()
                .eq(AcgWork::getStatus, STATUS_ENABLED)
                .orderByDesc(AcgWork::getHeatScore)
                .orderByDesc(AcgWork::getId)
                .last("LIMIT 200");
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            w.and(x -> x.like(AcgWork::getName, kw).or().like(AcgWork::getOriginalName, kw));
        }
        return workMapper.selectList(w).stream()
                .map(e -> new AcgOptionVO(e.getId(), e.getName(), e.getWorkType(),
                        AcgEnums.typeText(e.getWorkType()), e.getReleaseYear(), e.getHeatScore()))
                .toList();
    }

    /** 热度趋势：按日期正序返回，前端直接画折线 */
    public List<AcgHeatPointVO> trend(Long id, Integer days) {
        getExisting(id);
        int span = days == null ? DEFAULT_TREND_DAYS : Math.min(Math.max(days, 7), MAX_TREND_DAYS);
        LocalDate from = LocalDate.now().minusDays(span);
        List<AcgHeatLog> logs = heatLogMapper.selectList(Wrappers.<AcgHeatLog>lambdaQuery()
                .eq(AcgHeatLog::getWorkId, id)
                .ge(AcgHeatLog::getRecordDate, from)
                .orderByAsc(AcgHeatLog::getRecordDate));
        return logs.stream()
                .map(l -> new AcgHeatPointVO(l.getRecordDate(), l.getHeatScore(), l.getMonthlySales(), l.getRemark()))
                .toList();
    }

    /* ================= 维护 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(AcgWorkSaveRequest req) {
        currentUserService.requireManager("维护 ACG 作品");
        validate(req);
        checkNameUnique(req.getName(), req.getWorkType(), null);
        checkCodeUnique(req.getWorkCode(), null);

        AcgWork work = new AcgWork();
        apply(work, req);
        workMapper.insert(work);

        // 新增时填了热度，就顺手留一个快照，否则这个作品在趋势图上没有起点
        if (req.getHeatScore() != null) {
            upsertHeatLog(work.getId(), LocalDate.now(), req.getHeatScore(),
                    req.getMonthlySales(), "新增作品时录入");
        }
        log.info("新增 ACG 作品: id={} name={} type={}", work.getId(), work.getName(), work.getWorkType());
        return work.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AcgWorkSaveRequest req) {
        currentUserService.requireManager("维护 ACG 作品");
        validate(req);
        AcgWork existing = getExisting(id);
        checkNameUnique(req.getName(), req.getWorkType(), id);
        checkCodeUnique(req.getWorkCode(), id);

        AcgWork work = new AcgWork();
        work.setId(id);
        apply(work, req);
        // 前端没传热度时不动原值；传了且发生变化，记一条当日快照，保证趋势与列表一致
        if (req.getHeatScore() != null && !req.getHeatScore().equals(existing.getHeatScore())) {
            work.setHeatUpdatedAt(LocalDateTime.now());
            upsertHeatLog(id, LocalDate.now(), req.getHeatScore(), req.getMonthlySales(), "编辑作品时修改");
        }
        workMapper.updateById(work);
        log.info("编辑 ACG 作品: id={} name={}", id, req.getName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        currentUserService.requireManager("删除 ACG 作品");
        getExisting(id);
        workMapper.deleteById(id);
        // 快照一并逻辑删除，避免以后同 id 复用时读到旧曲线
        heatLogMapper.delete(Wrappers.<AcgHeatLog>lambdaQuery().eq(AcgHeatLog::getWorkId, id));
        log.info("删除 ACG 作品: id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        currentUserService.requireManager("修改 ACG 作品状态");
        if (!Integer.valueOf(STATUS_ENABLED).equals(status) && !Integer.valueOf(STATUS_DISABLED).equals(status)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "状态值只能是 0 或 1");
        }
        getExisting(id);
        AcgWork work = new AcgWork();
        work.setId(id);
        work.setStatus(status);
        workMapper.updateById(work);
        log.info("ACG 作品状态变更: id={} status={}", id, status);
    }

    /**
     * 录入当期热度：写一条快照（同一天重复录入则覆盖当天），并同步主表当前值。
     * <p>
     * 这是趋势分析的唯一数据来源，日常运营应该走这个接口，而不是去编辑档案。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordHeat(Long id, HeatRecordRequest req) {
        currentUserService.requireManager("录入 ACG 作品热度");
        AcgWork existing = getExisting(id);

        LocalDate date = req.getRecordDate() == null ? LocalDate.now() : req.getRecordDate();
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "记录日期不能晚于今天");
        }

        upsertHeatLog(id, date, req.getHeatScore(), req.getMonthlySales(), req.getRemark());

        AcgWork work = new AcgWork();
        work.setId(id);
        work.setHeatScore(req.getHeatScore());
        if (req.getMonthlySales() != null) {
            work.setMonthlySales(req.getMonthlySales());
        }
        work.setHeatUpdatedAt(LocalDateTime.now());
        workMapper.updateById(work);
        log.info("录入 ACG 热度: id={} name={} date={} heat={}（原 {}）",
                id, existing.getName(), date, req.getHeatScore(), existing.getHeatScore());
    }

    /* ================= 内部方法 ================= */

    private void applySort(LambdaQueryWrapper<AcgWork> w, String sortBy) {
        switch (sortBy == null ? "heatDesc" : sortBy) {
            case "heatAsc" -> w.orderByAsc(AcgWork::getHeatScore).orderByDesc(AcgWork::getId);
            case "salesDesc" -> w.orderByDesc(AcgWork::getMonthlySales).orderByDesc(AcgWork::getId);
            case "salesAsc" -> w.orderByAsc(AcgWork::getMonthlySales).orderByDesc(AcgWork::getId);
            case "yearDesc" -> w.orderByDesc(AcgWork::getReleaseYear).orderByDesc(AcgWork::getId);
            case "createDesc" -> w.orderByDesc(AcgWork::getCreateTime).orderByDesc(AcgWork::getId);
            // 默认按热度倒序：目录页最常见的诉求是「现在什么最火」
            default -> w.orderByDesc(AcgWork::getHeatScore).orderByDesc(AcgWork::getId);
        }
    }

    private void validate(AcgWorkSaveRequest req) {
        if (!AcgEnums.isValidType(req.getWorkType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "作品类型不合法，可选值：动画 / 漫画 / 游戏 / 轻小说 / 剧场版 / 其他");
        }
        if (StringUtils.hasText(req.getReleaseStatus()) && !AcgEnums.isValidReleaseStatus(req.getReleaseStatus())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "连载状态不合法，可选值：连载中 / 已完结 / 待播出");
        }
        if (req.getHeatScore() != null && (req.getHeatScore() < 0 || req.getHeatScore() > 100)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "热度必须在 0~100 之间");
        }
        if (req.getMonthlySales() != null && req.getMonthlySales() < 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "月销量不能为负数");
        }
        if (req.getTotalSales() != null && req.getTotalSales() < 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "累计销量不能为负数");
        }
    }

    /** 同一部作品不能重复录入：名称 + 类型 在未删除记录内唯一 */
    private void checkNameUnique(String name, String type, Long excludeId) {
        LambdaQueryWrapper<AcgWork> w = Wrappers.<AcgWork>lambdaQuery()
                .eq(AcgWork::getName, name.trim())
                .eq(AcgWork::getWorkType, type);
        if (excludeId != null) {
            w.ne(AcgWork::getId, excludeId);
        }
        w.last("LIMIT 1");
        if (workMapper.selectOne(w) != null) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS,
                    "该作品已存在：" + name.trim() + "（" + AcgEnums.typeText(type) + "）");
        }
    }

    private void checkCodeUnique(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        String trimmed = code.trim();
        LambdaQueryWrapper<AcgWork> w = Wrappers.<AcgWork>lambdaQuery().eq(AcgWork::getWorkCode, trimmed);
        if (excludeId != null) {
            w.ne(AcgWork::getId, excludeId);
        }
        w.last("LIMIT 1");
        if (workMapper.selectOne(w) != null) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "作品编号已存在：" + trimmed);
        }
    }

    private void apply(AcgWork work, AcgWorkSaveRequest req) {
        work.setWorkCode(StringUtils.hasText(req.getWorkCode()) ? req.getWorkCode().trim() : null);
        work.setName(req.getName().trim());
        work.setOriginalName(blankToNull(req.getOriginalName()));
        work.setWorkType(req.getWorkType());
        work.setIpOwner(blankToNull(req.getIpOwner()));
        work.setStudio(blankToNull(req.getStudio()));
        work.setAuthor(blankToNull(req.getAuthor()));
        work.setReleaseYear(req.getReleaseYear());
        work.setReleaseStatus(StringUtils.hasText(req.getReleaseStatus())
                ? req.getReleaseStatus() : AcgEnums.STATUS_FINISHED);
        work.setHeatScore(req.getHeatScore());
        work.setMonthlySales(req.getMonthlySales());
        work.setTotalSales(req.getTotalSales());
        work.setTags(AcgWorkConverter.joinTags(req.getTags()));
        work.setCoverImage(blankToNull(req.getCoverImage()));
        work.setRemark(blankToNull(req.getRemark()));
        work.setStatus(req.getStatus() == null ? STATUS_ENABLED : req.getStatus());
    }

    /** 同作品同日只保留一条快照：有则更新，无则新增 */
    private void upsertHeatLog(Long workId, LocalDate date, Integer heat, Integer sales, String remark) {
        AcgHeatLog existing = heatLogMapper.selectOne(Wrappers.<AcgHeatLog>lambdaQuery()
                .eq(AcgHeatLog::getWorkId, workId)
                .eq(AcgHeatLog::getRecordDate, date)
                .last("LIMIT 1"));

        if (existing != null) {
            AcgHeatLog update = new AcgHeatLog();
            update.setId(existing.getId());
            update.setHeatScore(heat);
            update.setMonthlySales(sales);
            update.setRemark(blankToNull(remark));
            heatLogMapper.updateById(update);
            return;
        }

        AcgHeatLog log = new AcgHeatLog();
        log.setWorkId(workId);
        log.setRecordDate(date);
        log.setHeatScore(heat);
        log.setMonthlySales(sales == null ? 0 : sales);
        log.setRemark(blankToNull(remark));
        heatLogMapper.insert(log);
    }

    private AcgWork getExisting(Long id) {
        AcgWork work = workMapper.selectById(id);
        if (work == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "作品不存在或已被删除");
        }
        return work;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
