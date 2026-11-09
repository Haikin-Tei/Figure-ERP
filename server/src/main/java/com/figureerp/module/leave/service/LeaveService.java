package com.figureerp.module.leave.service;

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
import com.figureerp.module.leave.converter.LeaveConverter;
import com.figureerp.module.leave.converter.LeaveType;
import com.figureerp.module.leave.dto.LeaveQuery;
import com.figureerp.module.leave.dto.LeaveSaveRequest;
import com.figureerp.module.leave.entity.LeaveApply;
import com.figureerp.module.leave.mapper.LeaveApplyMapper;
import com.figureerp.module.leave.vo.LeaveSummaryVO;
import com.figureerp.module.leave.vo.LeaveVO;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 员工请假申请的查询、提交与审批。
 * <p>
 * 业务规则：
 * <ul>
 *   <li>只有「待审批」能编辑 / 删除 / 撤销；已通过、已驳回、已撤销都是终态；</li>
 *   <li>审批需要主管级及以上，且<b>不能审批自己提交的单</b>，避免自批自过；</li>
 *   <li>撤销只能本人操作（审批人不走撤销，走驳回）；</li>
 *   <li>请假天数由后端按起止时间算：跨自然日按含首尾天数，同一天按 4 小时为界记 0.5 / 1 天；</li>
 *   <li>单号 LV + yyyyMMdd + 4 位序号，按当天最大单号 +1 生成。</li>
 * </ul>
 * 可见范围：执行级账号只看自己的申请；主管级及以上、人事岗可以看全部并代提。
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LeaveApplyMapper mapper;
    private final SysUserMapper userMapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<LeaveVO> page(LeaveQuery q) {
        SysUser me = currentUserService.require();
        LambdaQueryWrapper<LeaveApply> w = scope(me);

        if (canViewAll(me) && q.getUserId() != null) {
            w.eq(LeaveApply::getUserId, q.getUserId());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            w.and(x -> x.like(LeaveApply::getApplyNo, kw)
                    .or().like(LeaveApply::getUserName, kw)
                    .or().like(LeaveApply::getReason, kw));
        }
        if (StringUtils.hasText(q.getLeaveType())) {
            w.eq(LeaveApply::getLeaveType, q.getLeaveType());
        }
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(LeaveApply::getStatus, q.getStatus());
        }
        // 按请假开始时间过滤，含当天：起始 >= 当天 00:00，结束 < 次日 00:00
        if (q.getStartDate() != null) {
            w.ge(LeaveApply::getStartTime, q.getStartDate().atStartOfDay());
        }
        if (q.getEndDate() != null) {
            w.lt(LeaveApply::getEndTime, q.getEndDate().plusDays(1).atStartOfDay());
        }

        w.orderByDesc(LeaveApply::getCreateTime).orderByDesc(LeaveApply::getId);

        Page<LeaveApply> page = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        PageResult<LeaveVO> result = PageResult.of(page, LeaveConverter::toVO);
        // can* 开关依赖当前用户，转换完统一补一次
        result.records().forEach(vo -> fillPermissions(vo, me));
        return result;
    }

    public LeaveVO detail(Long id) {
        SysUser me = currentUserService.require();
        LeaveApply e = getExisting(id);
        requireCanView(e, me);
        LeaveVO vo = LeaveConverter.toVO(e);
        fillPermissions(vo, me);
        return vo;
    }

    /**
     * 顶部统计卡：待审批全量，其余按「请假开始时间在本月」统计。
     * <p>
     * 天数用 SUM 聚合而不是把记录拉进内存相加，员工多、请假单多时更稳。
     */
    public LeaveSummaryVO summary() {
        SysUser me = currentUserService.require();
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        LocalDate last = first.plusMonths(1).minusDays(1);

        LeaveSummaryVO vo = new LeaveSummaryVO();
        vo.setPendingCount(mapper.selectCount(scope(me).eq(LeaveApply::getStatus, ApplyStatus.PENDING)));
        vo.setMonthApprovedCount(mapper.selectCount(monthApprovedScope(me, first, last)));

        Map<String, Object> sum = mapper.selectMaps(monthApprovedSumWrapper(me, first, last))
                .stream().findFirst().orElse(Map.of());
        Object days = sum.get("days");
        vo.setMonthDays(days == null ? BigDecimal.ZERO : new BigDecimal(days.toString()));

        QueryWrapper<LeaveApply> distinctUser = new QueryWrapper<>();
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

    /** 字典：请假类型 + 单据状态，供前端筛选下拉使用 */
    public Map<String, Object> dicts() {
        Map<String, Object> data = new HashMap<>();
        data.put("leaveTypes", toDictList(LeaveType.dict()));
        data.put("statuses", toDictList(ApplyStatus.dict()));
        return data;
    }

    /* ================= 提交与维护 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(LeaveSaveRequest req) {
        SysUser me = currentUserService.require();
        SysUser target = resolveTarget(req.getUserId(), me);
        validateTimes(req.getStartTime(), req.getEndTime());

        LeaveApply e = new LeaveApply();
        e.setApplyNo(generateApplyNo());
        e.setUserId(target.getId());
        e.setUserName(target.getRealName());
        e.setRole(target.getRole());
        e.setLeaveType(req.getLeaveType());
        e.setStartTime(req.getStartTime());
        e.setEndTime(req.getEndTime());
        e.setDurationDays(calcDays(req.getStartTime(), req.getEndTime()));
        e.setReason(req.getReason().trim());
        e.setStatus(ApplyStatus.PENDING);
        mapper.insert(e);
        return e.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, LeaveSaveRequest req) {
        SysUser me = currentUserService.require();
        LeaveApply e = getExisting(id);
        requirePending(e, "编辑");
        requireCanOperate(e, me, "编辑");
        validateTimes(req.getStartTime(), req.getEndTime());

        if (req.getUserId() != null && !Objects.equals(req.getUserId(), e.getUserId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "编辑时不能更换申请人，请删除后重新提交");
        }

        // 只写允许改的列：状态、审批人、单号这些不能被编辑覆盖
        mapper.update(null, Wrappers.<LeaveApply>lambdaUpdate()
                .eq(LeaveApply::getId, id)
                .eq(LeaveApply::getStatus, ApplyStatus.PENDING)
                .set(LeaveApply::getLeaveType, req.getLeaveType())
                .set(LeaveApply::getStartTime, req.getStartTime())
                .set(LeaveApply::getEndTime, req.getEndTime())
                .set(LeaveApply::getDurationDays, calcDays(req.getStartTime(), req.getEndTime()))
                .set(LeaveApply::getReason, req.getReason().trim()));
    }

    /** 撤销：仅本人 + 待审批 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        SysUser me = currentUserService.require();
        LeaveApply e = getExisting(id);
        if (!Objects.equals(e.getUserId(), me.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能撤销自己提交的请假申请");
        }
        requirePending(e, "撤销");

        int rows = mapper.update(null, Wrappers.<LeaveApply>lambdaUpdate()
                .eq(LeaveApply::getId, id)
                .eq(LeaveApply::getStatus, ApplyStatus.PENDING)
                .set(LeaveApply::getStatus, ApplyStatus.CANCELLED));
        if (rows == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "单据状态已变更，请刷新后重试");
        }
    }

    /**
     * 审批：主管级及以上，且不能审自己的单。
     * <p>
     * update 条件里带上 {@code status = P}，配合影响行数判断，防止两个人同时点审批时
     * 后点的那个把已通过的单又改成驳回。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, ApproveRequest req) {
        SysUser me = currentUserService.requireManager("审批请假申请");
        LeaveApply e = getExisting(id);
        if (Objects.equals(e.getUserId(), me.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能审批自己提交的请假申请，请交给其他主管");
        }
        requirePending(e, ApplyStatus.APPROVED.equals(req.getResult()) ? "通过" : "驳回");
        if (ApplyStatus.REJECTED.equals(req.getResult()) && !StringUtils.hasText(req.getRemark())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "驳回时请填写驳回原因");
        }

        int rows = mapper.update(null, Wrappers.<LeaveApply>lambdaUpdate()
                .eq(LeaveApply::getId, id)
                .eq(LeaveApply::getStatus, ApplyStatus.PENDING)
                .set(LeaveApply::getStatus, req.getResult())
                .set(LeaveApply::getApproverId, me.getId())
                .set(LeaveApply::getApproverName, me.getRealName())
                .set(LeaveApply::getApproveTime, LocalDateTime.now())
                .set(LeaveApply::getApproveRemark, blankToNull(req.getRemark())));
        if (rows == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "单据已被处理，请刷新后查看最新状态");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser me = currentUserService.require();
        LeaveApply e = getExisting(id);
        requirePending(e, "删除");
        requireCanOperate(e, me, "删除");
        mapper.deleteById(id);
    }

    /* ================= 内部方法 ================= */

    private LambdaQueryWrapper<LeaveApply> scope(SysUser me) {
        LambdaQueryWrapper<LeaveApply> w = Wrappers.lambdaQuery();
        if (!canViewAll(me)) {
            w.eq(LeaveApply::getUserId, me.getId());
        }
        return w;
    }

    private LambdaQueryWrapper<LeaveApply> monthApprovedScope(SysUser me, LocalDate first, LocalDate last) {
        return scope(me)
                .eq(LeaveApply::getStatus, ApplyStatus.APPROVED)
                .ge(LeaveApply::getStartTime, first.atStartOfDay())
                .lt(LeaveApply::getEndTime, last.plusDays(1).atStartOfDay());
    }

    /**
     * 本月已通过的天数合计。
     * <p>
     * 聚合列必须用 QueryWrapper 拼字符串（LambdaQueryWrapper 只支持传实体字段），
     * 条件与 {@link #monthApprovedScope} 保持一致，避免两处口径走偏。
     */
    private QueryWrapper<LeaveApply> monthApprovedSumWrapper(SysUser me, LocalDate first, LocalDate last) {
        QueryWrapper<LeaveApply> w = new QueryWrapper<>();
        w.select("COALESCE(SUM(duration_days), 0) AS days")
                .eq("status", ApplyStatus.APPROVED)
                .ge("start_time", first.atStartOfDay())
                .lt("end_time", last.plusDays(1).atStartOfDay());
        if (!canViewAll(me)) {
            w.eq("user_id", me.getId());
        }
        return w;
    }

    /** 能看全部申请：主管级及以上，或人事岗 */
    private boolean canViewAll(SysUser user) {
        return UserRole.canManage(user.getRole()) || UserRole.isHr(user.getRole());
    }

    /** 能改 / 删别人的单：同样是主管 / 人事，且单据还是待审批 */
    private boolean canOperateOthers(SysUser user) {
        return canViewAll(user);
    }

    /** 补权限开关：四个按钮能不能点，前端直接照着渲染 */
    private void fillPermissions(LeaveVO vo, SysUser me) {
        boolean pending = ApplyStatus.PENDING.equals(vo.getStatus());
        boolean mine = Objects.equals(vo.getUserId(), me.getId());
        boolean manager = UserRole.canManage(me.getRole());

        vo.setCanApprove(pending && manager && !mine);
        vo.setCanCancel(pending && mine);
        vo.setCanEdit(pending && (mine || manager));
        vo.setCanDelete(pending && (mine || manager));
    }

    private void requireCanView(LeaveApply e, SysUser me) {
        if (!canViewAll(me) && !Objects.equals(e.getUserId(), me.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能查看自己提交的请假申请");
        }
    }

    /** 本人可以改自己的单；改别人的需要主管 / 人事 */
    private void requireCanOperate(LeaveApply e, SysUser me, String action) {
        if (Objects.equals(e.getUserId(), me.getId())) {
            return;
        }
        if (!canOperateOthers(me)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能" + action + "自己提交的请假申请");
        }
    }

    private void requirePending(LeaveApply e, String action) {
        if (!ApplyStatus.PENDING.equals(e.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "只有待审批的申请可以" + action + "，当前状态：" + ApplyStatus.labelOf(e.getStatus()));
        }
    }

    private LeaveApply getExisting(Long id) {
        LeaveApply e = mapper.selectById(id);
        if (e == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "请假申请不存在或已被删除");
        }
        return e;
    }

    /** 解析申请人：不传就是自己；代别人提交需要主管 / 人事权限 */
    private SysUser resolveTarget(Long userId, SysUser me) {
        if (userId == null || Objects.equals(userId, me.getId())) {
            return me;
        }
        if (!canOperateOthers(me)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能为自己提交请假申请，代提请联系主管或人事");
        }
        SysUser target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "员工不存在或已被删除");
        }
        return target;
    }

    private void validateTimes(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请假结束时间必须晚于开始时间");
        }
        if (Duration.between(start, end).toMinutes() < 60) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "单次请假不能少于 1 小时，请调整起止时间");
        }
        if (ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()) > 365) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "单次请假不能超过 365 天");
        }
    }

    /**
     * 请假天数：
     * <ul>
     *   <li>跨自然日 → 含首尾的天数（11-10 到 11-12 = 3 天）；</li>
     *   <li>同一天 → 时长超过 4 小时记 1 天，否则 0.5 天。</li>
     * </ul>
     * 按自然日算而不是按时长 ÷ 8，是因为跨周末 / 跨节的请假按工作日折算会更复杂，
     * 现在的口径简单可解释，需要精确到工作日的排班后再升级。
     */
    private BigDecimal calcDays(LocalDateTime start, LocalDateTime end) {
        long days = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()) + 1;
        if (days > 1) {
            return BigDecimal.valueOf(days);
        }
        return Duration.between(start, end).toMinutes() > 240
                ? BigDecimal.ONE : new BigDecimal("0.5");
    }

    /** 单号：LV + yyyyMMdd + 4 位序号。并发下极小概率重复，表上没建唯一索引，先按单线程使用 */
    private String generateApplyNo() {
        String prefix = "LV" + LocalDate.now().format(NO_DATE);
        LeaveApply last = mapper.selectOne(Wrappers.<LeaveApply>lambdaQuery()
                .likeRight(LeaveApply::getApplyNo, prefix)
                .orderByDesc(LeaveApply::getApplyNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (last != null && last.getApplyNo().length() > prefix.length()) {
            try {
                seq = Integer.parseInt(last.getApplyNo().substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                // 老数据里若有非数字后缀，退回从 1 开始，不影响插入
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
