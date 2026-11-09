package com.figureerp.module.attendance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.common.constant.UserRole;
import com.figureerp.module.attendance.converter.AttendanceRule;
import com.figureerp.module.attendance.entity.AttendanceRecord;
import com.figureerp.module.attendance.mapper.AttendanceRecordMapper;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 首次启动时写入最近 5 个工作日的考勤记录，方便直接看到列表与统计效果。
 * <p>
 * 只在考勤表为空时执行，已有数据（包括用户自己录的）不会被覆盖。
 * 依赖 sys_user 里已有启用的员工账号：一个都没有时直接跳过并打日志。
 * <p>
 * 数据是确定性生成的（不用随机数），重启两次看到的演示数据完全一样，
 * 验证脚本也因此可以放心断言「有迟到、有早退、销售岗免打卡」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.init-attendance.enabled", havingValue = "true", matchIfMissing = true)
public class AttendanceDataInitializer implements ApplicationRunner {

    /** 演示天数：最近 5 个工作日 */
    private static final int DEMO_DAYS = 5;
    /** 参与演示的员工上限，人多了列表会把其他模块的演示数据挤下去 */
    private static final int DEMO_USERS = 6;

    private final AttendanceRecordMapper attendanceMapper;
    private final SysUserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        try {
            Long count = attendanceMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("考勤记录已有 {} 条，跳过初始化", count);
                return;
            }

            List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getStatus, 1)
                    .orderByAsc(SysUser::getId)
                    .last("LIMIT " + DEMO_USERS));
            if (users.isEmpty()) {
                log.info("没有启用的员工账号，跳过考勤初始化");
                return;
            }

            List<LocalDate> days = recentWorkdays(DEMO_DAYS);
            int inserted = 0;
            for (int i = 0; i < users.size(); i++) {
                SysUser user = users.get(i);
                boolean sales = UserRole.isSales(user.getRole());
                for (int d = 0; d < days.size(); d++) {
                    LocalDate date = days.get(d);
                    // seq 让每个人的迟到 / 早退 / 缺卡落在不同日期，列表看起来更真实
                    int seq = i * 7 + d;

                    LocalTime inTime = switch (seq % 9) {
                        case 3 -> LocalTime.of(9, 23);   // 迟到
                        case 7 -> LocalTime.of(9, 41);   // 迟到
                        default -> LocalTime.of(8, 52);
                    };

                    LocalDateTime checkOut = null;
                    if (!sales) {
                        checkOut = switch (seq % 11) {
                            case 5 -> null;                                  // 未打卡
                            case 8 -> LocalDateTime.of(date, LocalTime.of(17, 28)); // 早退
                            default -> LocalDateTime.of(date, LocalTime.of(18, 6 + (seq % 20)));
                        };
                    }

                    AttendanceRecord record = new AttendanceRecord();
                    record.setUserId(user.getId());
                    record.setUserName(user.getRealName());
                    record.setRole(user.getRole());
                    record.setAttendDate(date);
                    record.setCheckInTime(LocalDateTime.of(date, inTime));
                    record.setCheckOutTime(checkOut);
                    record.setCheckInStatus(AttendanceRule.checkInStatusOf(record.getCheckInTime()));
                    record.setCheckOutStatus(AttendanceRule.checkOutStatusOf(user.getRole(), checkOut));
                    record.setWorkMinutes(AttendanceRule.workMinutesOf(record.getCheckInTime(), checkOut));
                    if (sales) {
                        record.setRemark("外勤");
                    }
                    attendanceMapper.insert(record);
                    inserted++;
                }
            }
            log.info("考勤记录初始化完成：{} 名员工 × 最近 {} 个工作日，共 {} 条", users.size(), DEMO_DAYS, inserted);
        } catch (Exception e) {
            // 初始化失败不能影响服务启动：记日志后继续，页面顶多是空列表
            log.warn("考勤记录初始化失败：{}", e.getMessage(), e);
        }
    }

    /** 最近 n 个工作日（不含今天），从近到远 */
    private List<LocalDate> recentWorkdays(int n) {
        List<LocalDate> days = new ArrayList<>(n);
        LocalDate cursor = LocalDate.now().minusDays(1);
        while (days.size() < n) {
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(cursor);
            }
            cursor = cursor.minusDays(1);
        }
        return days;
    }
}
