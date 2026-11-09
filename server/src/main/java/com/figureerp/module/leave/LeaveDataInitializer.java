package com.figureerp.module.leave;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.common.constant.ApplyStatus;
import com.figureerp.common.constant.UserRole;
import com.figureerp.module.leave.converter.LeaveType;
import com.figureerp.module.leave.entity.LeaveApply;
import com.figureerp.module.leave.mapper.LeaveApplyMapper;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 首次启动时写入 3 条请假单种子（待审批 / 已通过 / 已驳回各一条），方便直接看到列表与审批效果。
 * <p>
 * 只在请假表为空时执行。申请人依赖 sys_user 里已有的启用账号：
 * 按 id 顺序取前 3 个，找不到对应数量就少写几条，保证不会因为员工不足报错。
 * 审批人取第一个管理级账号（管理员 / 总经理），避免出现「自己审自己」的脏数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.init-leave.enabled", havingValue = "true", matchIfMissing = true)
public class LeaveDataInitializer implements ApplicationRunner {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LeaveApplyMapper leaveMapper;
    private final SysUserMapper userMapper;

    /** 一张种子单：类型 / 几天前开始 / 时长（天）/ 状态 / 事由 */
    private record Seed(String leaveType, int startDaysAgo, int days, String status, String reason) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = leaveMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("请假单已有 {} 条，跳过初始化", count);
                return;
            }

            List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getStatus, 1)
                    .orderByAsc(SysUser::getId)
                    .last("LIMIT 5"));
            if (users.isEmpty()) {
                log.info("没有启用的员工账号，跳过请假单初始化");
                return;
            }

            // 审批人：优先找管理级（管理员 / 总经理），一个都没有就用第一个账号
            SysUser approver = users.stream()
                    .filter(u -> UserRole.isAdmin(u.getRole()))
                    .findFirst().orElse(users.get(0));

            List<Seed> seeds = List.of(
                    new Seed(LeaveType.SICK, 1, 1, ApplyStatus.PENDING, "感冒发烧，医生建议休息一天"),
                    new Seed(LeaveType.PERSONAL, 6, 2, ApplyStatus.APPROVED, "家里老人住院，需要回去照看几天"),
                    new Seed(LeaveType.ANNUAL, 12, 3, ApplyStatus.REJECTED, "计划出行，与月末盘点冲突")
            );

            String prefix = "LV" + LocalDate.now().format(NO_DATE);
            int seq = 1;
            int inserted = 0;
            int userIdx = 0;
            for (Seed s : seeds) {
                // 跳过审批人自己（第一个账号通常是管理员）：宁可少写一条，也不要出现自己审自己
                while (userIdx < users.size() && users.get(userIdx).getId().equals(approver.getId())) {
                    userIdx++;
                }
                if (userIdx >= users.size()) {
                    break;
                }
                SysUser applicant = users.get(userIdx++);

                LocalDateTime start = LocalDate.now().minusDays(s.startDaysAgo()).atTime(9, 0);
                LocalDateTime end = start.toLocalDate().plusDays(s.days() - 1).atTime(18, 0);

                LeaveApply e = new LeaveApply();
                e.setApplyNo(prefix + String.format("%04d", seq++));
                e.setUserId(applicant.getId());
                e.setUserName(applicant.getRealName());
                e.setRole(applicant.getRole());
                e.setLeaveType(s.leaveType());
                e.setStartTime(start);
                e.setEndTime(end);
                e.setDurationDays(daysOf(start, end));
                e.setReason(s.reason());
                e.setStatus(s.status());

                if (!ApplyStatus.PENDING.equals(s.status())) {
                    e.setApproverId(approver.getId());
                    e.setApproverName(approver.getRealName());
                    e.setApproveTime(LocalDateTime.now().minusDays(1));
                    e.setApproveRemark(ApplyStatus.APPROVED.equals(s.status())
                            ? "已安排好工作交接，同意"
                            : "月末盘点人手紧，建议改期");
                }
                leaveMapper.insert(e);
                inserted++;
            }
            log.info("请假单初始化完成：{} 条", inserted);
        } catch (Exception e) {
            log.warn("请假单初始化失败：{}", e.getMessage(), e);
        }
    }

    /** 与 LeaveService.calcDays 同口径：跨天按含首尾天数，同一天按 4 小时为界记 0.5 / 1 天 */
    private BigDecimal daysOf(LocalDateTime start, LocalDateTime end) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()) + 1;
        if (days > 1) {
            return BigDecimal.valueOf(days);
        }
        return Duration.between(start, end).toMinutes() > 240 ? BigDecimal.ONE : new BigDecimal("0.5");
    }
}
