package com.figureerp.module.overtime.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.constant.ApplyStatus;
import com.figureerp.common.constant.UserRole;
import com.figureerp.common.dto.ApproveRequest;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.overtime.converter.OvertimeConverter;
import com.figureerp.module.overtime.converter.OvertimeType;
import com.figureerp.module.overtime.dto.OvertimeQuery;
import com.figureerp.module.overtime.dto.OvertimeSaveRequest;
import com.figureerp.module.overtime.entity.OvertimeApply;
import com.figureerp.module.overtime.mapper.OvertimeApplyMapper;
import com.figureerp.module.overtime.vo.OvertimeSummaryVO;
import com.figureerp.module.overtime.vo.OvertimeVO;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 员工加班申请的查询、提交与审批。
 * <p>
 * 规则与请假申请完全一致（只有待审批可改、审批不可自审、撤销只限本人），
 * 差别在时长：按小时计、保留 1 位小数，允许跨天，单次上限 24 小时。
 */
@Service
@RequiredArgsConstructor
public class OvertimeService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OvertimeApplyMapper mapper;
    private final SysUserMapper userMapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<OvertimeVO> page(OvertimeQuery q) {
        SysUser me = currentUserService.require();
        LambdaQueryWrapper<OvertimeApply> w = scope(me);

        if (canViewAll(me) && q.getUserId() != null) {
            w.eq(OvertimeApply::getUserId, q.getUserId());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            w.and(x -> x.like(OvertimeApply::getApplyNo, kw)
                    .or().like(OvertimeApply::getUserName, kw)
                    .or().like(OvertimeApply::getReason, kw));
        }
        if (StringUtils.hasText(q.getOvertimeType())) {
            w.eq(OvertimeApply::getOvertimeType, q.getOvertimeType());
        }
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(OvertimeApply::getStatus, q.getStatus());
        }
        if (q.getStartDate() != null) {
            w.ge(OvertimeApply::getStartTime, q.getStartDate().atStartOfDay());
        }
        if (q.getEndDate() != null) {
            w.lt(OvertimeApply::getEndTime, q.getEndDate().plusDays(1).atStartOfDay());
        }

        w.orderByDesc(OvertimeApply::getCreateTime).orderByDesc(OvertimeApply::getId);

        Page<OvertimeApply> page = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        PageResult<OvertimeVO> result = PageResult.of(page, OvertimeConverter::toVO);
        result.records().forEach(vo -> fillPermissions(vo, me));
        return result;
    }

    public OvertimeVO detail(Long id) {
        SysUser me = currentUserService.require();
        OvertimeApply e = getExisting(id);
        requireCanView(e, me);
        OvertimeVO vo = OvertimeConverter.toVO(e);
        fillPermissions(vo, me);
        return vo;
    }

    public OvertimeSummaryVO summary() {
        SysUser me = currentUserService.require();
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        LocalDate last = first.plusMonths(1).minusDays(1);

        OvertimeSummaryVO vo = new OvertimeSummaryVO();
        vo.setPendingCount(mapper.selectCount(scope(me).eq(OvertimeApply::getStatus, ApplyStatus.PENDING)));
        vo.setMonthApprovedCount(mapper.selectCount(monthApprovedScope(me, first, last)));

        Map<String, Object> sum = mapper.selectMaps(monthApprovedSumWrapper(me, first, last))
                .stream().findFirst().orElse(Map.of());
        Object hours = sum.get("hours");
        vo.setMonthHours(hours == null ? BigDecimal.ZERO : new BigDecimal(hours.toString()));

        QueryWrapper<OvertimeApply> distinctUser = new QueryWrapper<>();
        distinctUser.select("DISTINCT user_id")
                .eq("status", ApplyStatus.APPROVED)
                .ge("start_time", first.atStartOfDay())
                .lt("end_time", last.plusDays(1).atStartOfDay());
        if (!canViewAll(me)) {
            distinctUser.eq("user_id", me.getId());
        }
        vo.setMonthStaffCount((long) mapper.selectObjs(distinctUser).size());
        return vo;
    }

    public Map<String, Object> dicts() {
        Map<String, Object> data = new HashMap<>();
        data.put("overtimeTypes", toDictList(OvertimeType.dict()));
        data.put("statuses", toDictList(ApplyStatus.dict()));
        return data;
    }

    /* ================= 提交与维护 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(OvertimeSaveRequest req) {
        SysUser me = currentUserService.require();
        SysUser target = resolveTarget(req.getUserId(), me);
        validateTimes(req.getStartTime(), req.getEndTime());

        OvertimeApply e = new OvertimeApply();
        e.setApplyNo(generateApplyNo());
        e.setUserId(target.getId());
        e.setUserName(target.getRealName());
        e.setRole(target.getRole());
        e.setOvertimeType(req.getOvertimeType());
        e.setStartTime(req.getStartTime());
        e.setEndTime(req.getEndTime());
        e.setDurationHours(calcHours(req.getStartTime(), req.getEndTime()));
        e.setReason(req.getReason().trim());
        e.setStatus(ApplyStatus.PENDING);
        mapper.insert(e);
        return e.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OvertimeSaveRequest req) {
        SysUser me = currentUserService.require();
        OvertimeApply e = getExisting(id);
        requirePending(e, "编辑");
        requireCanOperate(e, me, "编辑");
        validateTimes(req.getStartTime(), req.getEndTime());

        if (req.getUserId() != null && !Objects.equals(req.getUserId(), e.getUserId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "编辑时不能更换申请人，请删除后重新提交");
        }

        mapper.update(null, Wrappers.<OvertimeApply>lambdaUpdate()
                .eq(OvertimeApply::getId, id)
                .eq(OvertimeApply::getStatus, ApplyStatus.PENDING)
                .set(OvertimeApply::getOvertimeType, req.getOvertimeType())
                .set(OvertimeApply::getStartTime, req.getStartTime())
                .set(OvertimeApply::getEndTime, req.getEndTime())
                .set(OvertimeApply::getDurationHours, calcHours(req.getStartTime(), req.getEndTime()))
                .set(OvertimeApply::getReason, req.getReason().trim()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        SysUser me = currentUserService.require();
        OvertimeApply e = getExisting(id);
        if (!Objects.equals(e.getUserId(), me.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能撤销自己提交的加班申请");
        }
        requirePending(e, "撤销");

        int rows = mapper.update(null, Wrappers.<OvertimeApply>lambdaUpdate()
                .eq(OvertimeApply::getId, id)
                .eq(OvertimeApply::getStatus, ApplyStatus.PENDING)
                .set(OvertimeApply::getStatus, ApplyStatus.CANCELLED));
        if (rows == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "单据状态已变更，请刷新后重试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, ApproveRequest req) {
        SysUser me = currentUserService.requireManager("审批加班申请");
        OvertimeApply e = getExisting(id);
        if (Objects.equals(e.getUserId(), me.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能审批自己提交的加班申请，请交给其他主管");
        }
        requirePending(e, ApplyStatus.APPROVED.equals(req.getResult()) ? "通过" : "驳回");
        if (ApplyStatus.REJECTED.equals(req.getResult()) && !StringUtils.hasText(req.getRemark())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "驳回时请填写驳回原因");
        }

        int rows = mapper.update(null, Wrappers.<OvertimeApply>lambdaUpdate()
                .eq(OvertimeApply::getId, id)
                .eq(OvertimeApply::getStatus, ApplyStatus.PENDING)
                .set(OvertimeApply::getStatus, req.getResult())
                .set(OvertimeApply::getApproverId, me.getId())
                .set(OvertimeApply::getApproverName, me.getRealName())
                .set(OvertimeApply::getApproveTime, LocalDateTime.now())
                .set(OvertimeApply::getApproveRemark, blankToNull(req.getRemark())));
        if (rows == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "单据已被处理，请刷新后查看最新状态");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser me = currentUserService.require();
        OvertimeApply e = getExisting(id);
        requirePending(e, "删除");
        requireCanOperate(e, me, "删除");
        mapper.deleteById(id);
    }

    /* ================= 内部方法 ================= */

    private LambdaQueryWrapper<OvertimeApply> scope(SysUser me) {
        LambdaQueryWrapper<OvertimeApply> w = Wrappers.lambdaQuery();
        if (!canViewAll(me)) {
            w.eq(OvertimeApply::getUserId, me.getId());
        }
        return w;
    }

    private LambdaQueryWrapper<OvertimeApply> monthApprovedScope(SysUser me, LocalDate first, LocalDate last) {
        return scope(me)
                .eq(OvertimeApply::getStatus, ApplyStatus.APPROVED)
                .ge(OvertimeApply::getStartTime, first.atStartOfDay())
                .lt(OvertimeApply::getEndTime, last.plusDays(1).atStartOfDay());
    }

    private QueryWrapper<OvertimeApply> monthApprovedSumWrapper(SysUser me, LocalDate first, LocalDate last) {
        QueryWrapper<OvertimeApply> w = new QueryWrapper<>();
        w.select("COALESCE(SUM(duration_hours), 0) AS hours")
                .eq("status", ApplyStatus.APPROVED)
                .ge("start_time", first.atStartOfDay())
                .lt("end_time", last.plusDays(1).atStartOfDay());
        if (!canViewAll(me)) {
            w.eq("user_id", me.getId());
        }
        return w;
    }

    private boolean canViewAll(SysUser user) {
        return UserRole.canManage(user.getRole()) || UserRole.isHr(user.getRole());
    }

    private void fillPermissions(OvertimeVO vo, SysUser me) {
        boolean pending = ApplyStatus.PENDING.equals(vo.getStatus());
        boolean mine = Objects.equals(vo.getUserId(), me.getId());
        boolean manager = UserRole.canManage(me.getRole());

        vo.setCanApprove(pending && manager && !mine);
        vo.setCanCancel(pending && mine);
        vo.setCanEdit(pending && (mine || manager));
        vo.setCanDelete(pending && (mine || manager));
    }

    private void requireCanView(OvertimeApply e, SysUser me) {
        if (!canViewAll(me) && !Objects.equals(e.getUserId(), me.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能查看自己提交的加班申请");
        }
    }

    private void requireCanOperate(OvertimeApply e, SysUser me, String action) {
        if (Objects.equals(e.getUserId(), me.getId())) {
            return;
        }
        if (!canViewAll(me)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能" + action + "自己提交的加班申请");
        }
    }

    private void requirePending(OvertimeApply e, String action) {
        if (!ApplyStatus.PENDING.equals(e.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "只有待审批的申请可以" + action + "，当前状态：" + ApplyStatus.labelOf(e.getStatus()));
        }
    }

    private OvertimeApply getExisting(Long id) {
        OvertimeApply e = mapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "加班申请不存在或已被删除");
        }
        return e;
    }

    private SysUser resolveTarget(Long userId, SysUser me) {
        if (userId == null || Objects.equals(userId, me.getId())) {
            return me;
        }
        if (!canViewAll(me)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能为自己提交加班申请，代提请联系主管或人事");
        }
        SysUser target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "员工不存在或已被删除");
        }
        return target;
    }

    /** 起止校验：结束要晚于开始、至少 0.5 小时、单次不超过 24 小时（允许跨天） */
    private void validateTimes(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "加班结束时间必须晚于开始时间");
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 30) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "单次加班不能少于 30 分钟");
        }
        if (minutes > 24 * 60) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "单次加班不能超过 24 小时，请分次提交");
        }
    }

    /** 加班时长（小时）：分钟数 ÷ 60，四舍五入保留 1 位小数 */
    private BigDecimal calcHours(LocalDateTime start, LocalDateTime end) {
        return BigDecimal.valueOf(Duration.between(start, end).toMinutes())
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    /** 单号：OT + yyyyMMdd + 4 位序号 */
    private String generateApplyNo() {
        String prefix = "OT" + LocalDate.now().format(NO_DATE);
        OvertimeApply last = mapper.selectOne(Wrappers.<OvertimeApply>lambdaQuery()
                .likeRight(OvertimeApply::getApplyNo, prefix)
                .orderByDesc(OvertimeApply::getApplyNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getApplyNo().length() > prefix.length()) {
            try {
                seq = Integer.parseInt(last.getApplyNo().substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                // 非数字后缀时退回从 1 开始
            }
        }
        return prefix + String.format("%04d", seq);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static List<Map<String, String>> toDictList(Map<String, String> dict) {
        return dict.entrySet().stream()
                .map(e -> Map.of("code", e.getKey(), "label", e.getValue()))
                .toList();
    }
}
