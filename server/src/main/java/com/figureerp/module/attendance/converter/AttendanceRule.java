package com.figureerp.module.attendance.converter;

import com.figureerp.common.constant.UserRole;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考勤判定规则（唯一实现）。
 * <p>
 * 为什么单独抽一个类：录入（{@code AttendanceService}）和演示数据初始化
 * （{@code AttendanceDataInitializer}）都要算同一套状态，规则散在两处早晚会不一致。
 * 以后要改上班时间、加弹性打卡，只改这里的常量和方法即可。
 * <p>
 * 当前规则（按用户确认的口径）：
 * <ul>
 *   <li>上班晚于 <b>09:00</b> → 迟到；</li>
 *   <li>下班早于 <b>18:00</b> → 早退，没填 → 未打卡；</li>
 *   <li>销售岗不判下班，统一「免打卡」，下班时间强制置空。</li>
 * </ul>
 */
public final class AttendanceRule {

    /** 上班时间：晚于这个时刻算迟到 */
    public static final LocalTime WORK_START = LocalTime.of(9, 0);
    /** 下班时间：早于这个时刻算早退 */
    public static final LocalTime WORK_END = LocalTime.of(18, 0);

    private AttendanceRule() {
    }

    /** 上班状态：N 正常 / L 迟到 */
    public static String checkInStatusOf(LocalDateTime checkIn) {
        return checkIn.toLocalTime().isAfter(WORK_START)
                ? AttendanceStatus.IN_LATE : AttendanceStatus.IN_NORMAL;
    }

    /**
     * 下班状态：销售岗 X 免打卡；没填 M 未打卡；早于 18:00 E 早退；否则 N 正常。
     *
     * @param checkOut 已经过 {@link #effectiveCheckOut} 处理的下班时间
     */
    public static String checkOutStatusOf(String role, LocalDateTime checkOut) {
        if (UserRole.isSales(role)) {
            return AttendanceStatus.OUT_EXEMPT;
        }
        if (checkOut == null) {
            return AttendanceStatus.OUT_MISSING;
        }
        return checkOut.toLocalTime().isBefore(WORK_END)
                ? AttendanceStatus.OUT_EARLY : AttendanceStatus.OUT_NORMAL;
    }

    /**
     * 实际生效的下班时间：销售岗一律为 null。
     * <p>
     * 前端误传了也按免打卡处理 —— 销售跑外勤下班时间不固定，
     * 存下来只会污染早退统计，没有实际意义。
     */
    public static LocalDateTime effectiveCheckOut(String role, LocalDateTime checkOut) {
        return UserRole.isSales(role) ? null : checkOut;
    }

    /** 当日工时（分钟）：下班为空时返回 null（免打卡 / 未打卡都不算工时） */
    public static Integer workMinutesOf(LocalDateTime checkIn, LocalDateTime checkOut) {
        if (checkIn == null || checkOut == null) {
            return null;
        }
        return (int) Duration.between(checkIn, checkOut).toMinutes();
    }
}
