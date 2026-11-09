package com.figureerp.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.figureerp.common.constant.UserRole;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.attendance.converter.AttendanceConverter;
import com.figureerp.module.attendance.converter.AttendanceRule;
import com.figureerp.module.attendance.converter.AttendanceStatus;
import com.figureerp.module.attendance.dto.AttendanceQuery;
import com.figureerp.module.attendance.dto.AttendanceSaveRequest;
import com.figureerp.module.attendance.entity.AttendanceRecord;
import com.figureerp.module.attendance.mapper.AttendanceRecordMapper;
import com.figureerp.module.attendance.vo.AttendanceSummaryVO;
import com.figureerp.module.attendance.vo.AttendanceVO;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import com.figureerp.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 员工考勤的查询与录入。
 * <p>
 * 业务规则：
 * <ul>
 *   <li>一人一天只有一行，重复录入会被拦下（表上没建 UNIQUE，原因是逻辑删除，见 schema.sql 说明）；</li>
 *   <li>考勤日期不能晚于今天；上班 / 下班时间必须落在考勤日期当天，且下班要晚于上班；</li>
 *   <li>迟到：上班时间晚于 <b>09:00</b>；早退：下班时间早于 <b>18:00</b>；没填下班时间记「未打卡」；</li>
 *   <li>销售岗（{@link UserRole#isSales}）只录上班时间，下班时间一律清空并记「免打卡」；</li>
 *   <li>状态与工时全部由后端算，前端传的值一律忽略，避免两边规则不一致。</li>
 * </ul>
 * 可见范围：执行级账号只看自己的考勤；主管级及以上、人事岗可以看全部并给他人录入
 * （项目里没有部门模型，所以按角色层级整体放行，而不是按所属部门过滤）。
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordMapper mapper;
    private final SysUserMapper userMapper;
    private final CurrentUserService currentUserService;

    /* ================= 查询 ================= */

    public PageResult<AttendanceVO> page(AttendanceQuery q) {
        SysUser me = currentUserService.require();
        LambdaQueryWrapper<AttendanceRecord> w = scope(me);

        if (canOperateOthers(me) && q.getUserId() != null) {
            w.eq(AttendanceRecord::getUserId, q.getUserId());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            // 登录账号在 sys_user 里，考勤表只存了 userId / 姓名快照，先按账号查一批 id 再串 OR
            Set<Long> idsByUsername = findUserIdsByKeyword(kw);
            w.and(x -> x.like(AttendanceRecord::getUserName, kw)
                    .or(!idsByUsername.isEmpty(), y -> y.in(AttendanceRecord::getUserId, idsByUsername)));
        }
        if (StringUtils.hasText(q.getCheckInStatus())) {
            w.eq(AttendanceRecord::getCheckInStatus, q.getCheckInStatus());
        }
        if (StringUtils.hasText(q.getCheckOutStatus())) {
            w.eq(AttendanceRecord::getCheckOutStatus, q.getCheckOutStatus());
        }
        if (q.getStartDate() != null) {
            w.ge(AttendanceRecord::getAttendDate, q.getStartDate());
        }
        if (q.getEndDate() != null) {
            w.le(AttendanceRecord::getAttendDate, q.getEndDate());
        }

        // 日期倒序 + id 倒序：同一天多人时顺序也稳定，不会翻页错位
        w.orderByDesc(AttendanceRecord::getAttendDate).orderByDesc(AttendanceRecord::getId);

        Page<AttendanceRecord> page = mapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        return PageResult.of(page, AttendanceConverter::toVO);
    }

    public AttendanceVO detail(Long id) {
        SysUser me = currentUserService.require();
        AttendanceRecord record = getExisting(id);
        if (!canOperateOthers(me) && !Objects.equals(record.getUserId(), me.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能查看自己的考勤记录");
        }
        return AttendanceConverter.toVO(record);
    }

    /**
     * 顶部统计卡：本月（1 号到今天所在的自然月）口径，与列表的数据范围一致。
     * <p>
     * 用 5 次轻量 count 查询而不是把整月记录拉进内存再遍历 —— 员工多、月份长的时候差别很明显。
     */
    public AttendanceSummaryVO summary() {
        SysUser me = currentUserService.require();
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        LocalDate last = first.plusMonths(1).minusDays(1);

        AttendanceSummaryVO vo = new AttendanceSummaryVO();
        vo.setMonthRecords(mapper.selectCount(monthScope(me, first, last)));
        vo.setLateCount(mapper.selectCount(monthScope(me, first, last)
                .eq(AttendanceRecord::getCheckInStatus, AttendanceStatus.IN_LATE)));
        vo.setEarlyCount(mapper.selectCount(monthScope(me, first, last)
                .eq(AttendanceRecord::getCheckOutStatus, AttendanceStatus.OUT_EARLY)));
        vo.setMissingCount(mapper.selectCount(monthScope(me, first, last)
                .eq(AttendanceRecord::getCheckOutStatus, AttendanceStatus.OUT_MISSING)));

        // 去重员工数：selectObjs 只取 user_id 一列，内存里去重计数
        QueryWrapper<AttendanceRecord> distinctUser = new QueryWrapper<>();
        distinctUser.select("DISTINCT user_id").ge("attend_date", first).le("attend_date", last);
        if (!canOperateOthers(me)) {
            distinctUser.eq("user_id", me.getId());
        }
        List<Object> userIds = mapper.selectObjs(distinctUser);
        vo.setStaffCount((long) userIds.size());
        return vo;
    }

    /** 字典：上班 / 下班状态，供前端筛选下拉使用 */
    public Map<String, Object> dicts() {
        Map<String, Object> data = new HashMap<>();
        data.put("checkInStatuses", toDictList(AttendanceStatus.inDict()));
        data.put("checkOutStatuses", toDictList(AttendanceStatus.outDict()));
        return data;
    }

    /* ================= 录入 ================= */

    @Transactional(rollbackFor = Exception.class)
    public Long create(AttendanceSaveRequest req) {
        SysUser me = currentUserService.require();
        SysUser target = resolveTarget(req.getUserId(), me);
        validate(req);

        // 一人一天一行：同一员工同一天已有记录时直接拦下，提示去编辑那一条
        Long dup = mapper.selectCount(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getUserId, target.getId())
                .eq(AttendanceRecord::getAttendDate, req.getAttendDate()));
        if (dup != null && dup > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS,
                    target.getRealName() + " 在 " + req.getAttendDate() + " 已有考勤记录，请直接编辑");
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setUserId(target.getId());
        record.setUserName(target.getRealName());
        record.setRole(target.getRole());
        record.setAttendDate(req.getAttendDate());
        fillTimes(record, req, target.getRole());
        mapper.insert(record);
        return record.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AttendanceSaveRequest req) {
        SysUser me = currentUserService.require();
        AttendanceRecord existing = getExisting(id);
        requireCanEdit(existing, me);
        validate(req);

        // 编辑不允许换人：换人等于另一条记录，请删掉重录，避免历史数据被悄悄改归属
        if (req.getUserId() != null && !Objects.equals(req.getUserId(), existing.getUserId())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "编辑时不能更换员工，请删除后重新录入");
        }

        Long dup = mapper.selectCount(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getUserId, existing.getUserId())
                .eq(AttendanceRecord::getAttendDate, req.getAttendDate())
                .ne(AttendanceRecord::getId, id));
        if (dup != null && dup > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS,
                    "该员工在 " + req.getAttendDate() + " 已有另一条考勤记录");
        }

        // 用 LambdaUpdateWrapper 逐列 set：checkOutTime 标了 FieldStrategy.ALWAYS，
        // 只有显式 set 才能把「原来有下班时间」改成 NULL（未打卡 / 免打卡），
        // 同时也不会像空实体 updateById 那样把别的列误清空。
        AttendanceRecord tmp = new AttendanceRecord();
        tmp.setAttendDate(req.getAttendDate());
        fillTimes(tmp, req, existing.getRole());

        mapper.update(null, Wrappers.<AttendanceRecord>lambdaUpdate()
                .eq(AttendanceRecord::getId, id)
                .set(AttendanceRecord::getAttendDate, tmp.getAttendDate())
                .set(AttendanceRecord::getCheckInTime, tmp.getCheckInTime())
                .set(AttendanceRecord::getCheckOutTime, tmp.getCheckOutTime())
                .set(AttendanceRecord::getCheckInStatus, tmp.getCheckInStatus())
                .set(AttendanceRecord::getCheckOutStatus, tmp.getCheckOutStatus())
                .set(AttendanceRecord::getWorkMinutes, tmp.getWorkMinutes())
                .set(AttendanceRecord::getRemark, blankToNull(req.getRemark())));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser me = currentUserService.require();
        AttendanceRecord existing = getExisting(id);
        requireCanEdit(existing, me);
        mapper.deleteById(id);
    }

    /* ================= 内部方法 ================= */

    /** 数据范围：执行级只能看自己，主管级及以上 / 人事看全部 */
    private LambdaQueryWrapper<AttendanceRecord> scope(SysUser me) {
        LambdaQueryWrapper<AttendanceRecord> w = Wrappers.lambdaQuery();
        if (!canOperateOthers(me)) {
            w.eq(AttendanceRecord::getUserId, me.getId());
        }
        return w;
    }

    private LambdaQueryWrapper<AttendanceRecord> monthScope(SysUser me, LocalDate first, LocalDate last) {
        return scope(me).ge(AttendanceRecord::getAttendDate, first).le(AttendanceRecord::getAttendDate, last);
    }

    /**
     * 能不能看 / 录别人的考勤：主管级及以上，或人事岗（人事专员是执行级，但考勤归人事管）。
     * <p>
     * 这里只放宽「看」和「录」，删除同样走这个判断 —— 删除是纠错动作，和录入是同一件事的两面。
     */
    private boolean canOperateOthers(SysUser user) {
        return UserRole.canManage(user.getRole()) || UserRole.isHr(user.getRole());
    }

    /** 解析本次要录的是谁：不传就是自己；传别人必须有权限 */
    private SysUser resolveTarget(Long userId, SysUser me) {
        if (userId == null || Objects.equals(userId, me.getId())) {
            return me;
        }
        if (!canOperateOthers(me)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能录入自己的考勤，代录请联系主管或人事");
        }
        SysUser target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "员工不存在或已被删除");
        }
        return target;
    }

    /** 本人可以改自己的记录；改别人的需要权限 */
    private void requireCanEdit(AttendanceRecord record, SysUser me) {
        if (Objects.equals(record.getUserId(), me.getId())) {
            return;
        }
        if (!canOperateOthers(me)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能修改自己的考勤记录");
        }
    }

    private AttendanceRecord getExisting(Long id) {
        AttendanceRecord record = mapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "考勤记录不存在或已被删除");
        }
        return record;
    }

    /** 日期相关校验：不能录未来、时间要落在当天、下班要晚于上班 */
    private void validate(AttendanceSaveRequest req) {
        if (req.getAttendDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "考勤日期不能晚于今天");
        }
        if (!req.getAttendDate().equals(req.getCheckInTime().toLocalDate())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "上班时间的日期必须与考勤日期一致");
        }
        if (req.getCheckOutTime() != null) {
            if (!req.getAttendDate().equals(req.getCheckOutTime().toLocalDate())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "下班时间的日期必须与考勤日期一致");
            }
            if (!req.getCheckOutTime().isAfter(req.getCheckInTime())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "下班时间必须晚于上班时间");
            }
        }
    }

    /**
     * 按统一规则算出上下班状态与工时，写进实体。
     * <p>
     * 判定逻辑全部在 {@link AttendanceRule} 里，这里只做取值与赋值，
     * 保证页面录入和演示数据初始化用的是同一套口径。
     */
    private void fillTimes(AttendanceRecord record, AttendanceSaveRequest req, String role) {
        LocalDateTime checkIn = req.getCheckInTime();
        LocalDateTime checkOut = AttendanceRule.effectiveCheckOut(role, req.getCheckOutTime());

        record.setCheckInTime(checkIn);
        record.setCheckOutTime(checkOut);
        record.setCheckInStatus(AttendanceRule.checkInStatusOf(checkIn));
        record.setCheckOutStatus(AttendanceRule.checkOutStatusOf(role, checkOut));
        record.setWorkMinutes(AttendanceRule.workMinutesOf(checkIn, checkOut));
        record.setRemark(blankToNull(req.getRemark()));
    }

    private Set<Long> findUserIdsByKeyword(String keyword) {
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .like(SysUser::getUsername, keyword))
                .stream().map(SysUser::getId).collect(Collectors.toSet());
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
