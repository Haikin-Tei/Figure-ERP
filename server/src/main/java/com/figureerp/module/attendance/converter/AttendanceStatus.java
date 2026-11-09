package com.figureerp.module.attendance.converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 考勤状态常量（与数据库 char(1) 取值一一对应）。
 * <p>
 * 上班、下班两套状态分开定义：
 * <ul>
 *   <li>上班 {@code check_in_status}：N 正常 / L 迟到（晚于 09:00）；</li>
 *   <li>下班 {@code check_out_status}：N 正常 / E 早退（早于 18:00）/ M 未打卡（没填下班时间）/ X 免打卡（销售岗）。</li>
 * </ul>
 * 文案 map 分开放在两个类里，避免前端下拉把「迟到」和「早退」混在同一个选项列表里。
 */
public final class AttendanceStatus {

    /* ---------------- 上班 ---------------- */

    public static final String IN_NORMAL = "N";
    public static final String IN_LATE = "L";

    private static final Map<String, String> IN_TEXT = new LinkedHashMap<>();

    static {
        IN_TEXT.put(IN_NORMAL, "正常");
        IN_TEXT.put(IN_LATE, "迟到");
    }

    /* ---------------- 下班 ---------------- */

    public static final String OUT_NORMAL = "N";
    public static final String OUT_EARLY = "E";
    /** 应打卡但没填下班时间（非销售岗留空） */
    public static final String OUT_MISSING = "M";
    /** 免打卡：销售岗只上下班卡 */
    public static final String OUT_EXEMPT = "X";

    private static final Map<String, String> OUT_TEXT = new LinkedHashMap<>();

    static {
        OUT_TEXT.put(OUT_NORMAL, "正常");
        OUT_TEXT.put(OUT_EARLY, "早退");
        OUT_TEXT.put(OUT_MISSING, "未打卡");
        OUT_TEXT.put(OUT_EXEMPT, "免打卡");
    }

    private AttendanceStatus() {
    }

    public static String inLabel(String status) {
        return IN_TEXT.getOrDefault(status, status);
    }

    public static String outLabel(String status) {
        return OUT_TEXT.getOrDefault(status, status);
    }

    /** 上班状态字典，供前端筛选下拉 */
    public static Map<String, String> inDict() {
        return new LinkedHashMap<>(IN_TEXT);
    }

    /** 下班状态字典，供前端筛选下拉 */
    public static Map<String, String> outDict() {
        return new LinkedHashMap<>(OUT_TEXT);
    }
}
