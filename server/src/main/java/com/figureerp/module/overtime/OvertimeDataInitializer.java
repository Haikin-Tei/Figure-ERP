package com.figureerp.module.overtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.common.constant.ApplyStatus;
import com.figureerp.common.constant.UserRole;
import com.figureerp.module.overtime.converter.OvertimeType;
import com.figureerp.module.overtime.entity.OvertimeApply;
import com.figureerp.module.overtime.mapper.OvertimeApplyMapper;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 首次启动时写入 3 条加班单种子（待审批 / 已通过 / 已驳回各一条）。
 * <p>
 * 只在加班表为空时执行；申请人取 sys_user 里前 3 个启用账号，
 * 审批人取第一个管理级账号，避免出现自己审自己。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.init-overtime.enabled", havingValue = "true", matchIfMissing = true)
public class OvertimeDataInitializer implements ApplicationRunner {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OvertimeApplyMapper overtimeMapper;
    private final SysUserMapper userMapper;

    /** 一张种子单：类型 / 几天前 / 开始钟点 / 时长（小时）/ 状态 / 事由 */
    private record Seed(String overtimeType, int daysAgo, int startHour, double hours,
                        String status, String reason) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = overtimeMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("加班单已有 {} 条，跳过初始化", count);
                return;
            }

            List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getStatus, 1)
                    .orderByAsc(SysUser::getId)
                    .last("LIMIT 5"));
            if (users.isEmpty()) {
                log.info("没有启用的员工账号，跳过加班单初始化");
                return;
            }

            SysUser approver = users.stream()
                    .filter(u -> UserRole.isAdmin(u.getRole()))
                    .findFirst().orElse(users.get(0));

            List<Seed> seeds = List.of(
                    new Seed(OvertimeType.REST_DAY, 1, 10, 4.0, ApplyStatus.PENDING,
                            "周六盘点库存，需要人手核对实物"),
                    new Seed(OvertimeType.WEEKDAY, 4, 18, 3.0, ApplyStatus.APPROVED,
                            "双十一大促，协助仓库打包发货"),
                    new Seed(OvertimeType.HOLIDAY, 20, 9, 6.0, ApplyStatus.REJECTED,
                            "节假日值班，但排班已满")
            );

            String prefix = "OT" + LocalDate.now().format(NO_DATE);
            int seq = 1;
            int inserted = 0;
            int userIdx = 0;
            for (Seed s : seeds) {
                // 跳过审批人自己（第一个账号通常是管理员），避免出现自己审自己
                while (userIdx < users.size() && users.get(userIdx).getId().equals(approver.getId())) {
                    userIdx++;
                }
                if (userIdx >= users.size()) {
                    break;
                }
                SysUser applicant = users.get(userIdx++);

                LocalDateTime start = LocalDate.now().minusDays(s.daysAgo()).atTime(s.startHour(), 0);
                LocalDateTime end = start.plusMinutes(Math.round(s.hours() * 60));

                OvertimeApply e = new OvertimeApply();
                e.setApplyNo(prefix + String.format("%04d", seq++));
                e.setUserId(applicant.getId());
                e.setUserName(applicant.getRealName());
                e.setRole(applicant.getRole());
                e.setOvertimeType(s.overtimeType());
                e.setStartTime(start);
                e.setEndTime(end);
                e.setDurationHours(hoursOf(start, end));
                e.setReason(s.reason());
                e.setStatus(s.status());

                if (!ApplyStatus.PENDING.equals(s.status())) {
                    e.setApproverId(approver.getId());
                    e.setApproverName(approver.getRealName());
                    e.setApproveTime(LocalDateTime.now().minusDays(1));
                    e.setApproveRemark(ApplyStatus.APPROVED.equals(s.status())
                            ? "确实在大促期间，同意，注意安排调休"
                            : "当天已有排班，建议改到下周");
                }
                overtimeMapper.insert(e);
                inserted++;
            }
            log.info("加班单初始化完成：{} 条", inserted);
        } catch (Exception e) {
            log.warn("加班单初始化失败：{}", e.getMessage(), e);
        }
    }

    /** 与 OvertimeService.calcHours 同口径：分钟 ÷ 60，保留 1 位小数 */
    private BigDecimal hoursOf(LocalDateTime start, LocalDateTime end) {
        return BigDecimal.valueOf(Duration.between(start, end).toMinutes())
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }
}
