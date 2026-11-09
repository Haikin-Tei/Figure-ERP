package com.figureerp.common.constant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 申请单状态常量（请假 / 加班共用，与数据库 char(1) 取值一一对应）。
 * <p>
 * 流转规则：P 待审批 →（主管审批）→ A 已通过 / R 已驳回；
 * P 状态下本人可撤销 → C 已撤销。A / R / C 都是终态，不能再改、再审批。
 */
public final class ApplyStatus {

    /** 待审批：可编辑、可删除、可撤销、可审批 */
    public static final String PENDING = "P";
    /** 已通过：终态 */
    public static final String APPROVED = "A";
    /** 已驳回：终态，需要重新走一张新单 */
    public static final String REJECTED = "R";
    /** 已撤销：本人撤回，终态 */
    public static final String CANCELLED = "C";

    /** 状态 → 文案，用 LinkedHashMap 保证字典接口返回顺序稳定 */
    private static final Map<String, String> TEXT = new LinkedHashMap<>();

    static {
        TEXT.put(PENDING, "待审批");
        TEXT.put(APPROVED, "已通过");
        TEXT.put(REJECTED, "已驳回");
        TEXT.put(CANCELLED, "已撤销");
    }

    private ApplyStatus() {
    }

    public static boolean isValid(String status) {
        return TEXT.containsKey(status);
    }

    public static String labelOf(String status) {
        return TEXT.getOrDefault(status, status);
    }

    /** 终态：不能再编辑 / 撤销 / 审批 */
    public static boolean isFinal(String status) {
        return APPROVED.equals(status) || REJECTED.equals(status) || CANCELLED.equals(status);
    }

    /** 字典项，供前端下拉框与字典接口使用 */
    public static Map<String, String> dict() {
        return new LinkedHashMap<>(TEXT);
    }
}
