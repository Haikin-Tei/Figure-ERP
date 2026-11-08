package com.figureerp.module.stock.converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 入库单状态常量（与数据库 char(1) 取值一一对应）。
 * <p>
 * 流转规则：D 待入库 →（确认入库）→ C 已入库；
 * D / C 都可以作废（X）；已作废的单不能改、不能再确认。
 */
public final class InboundStatus {

    /** 待入库（草稿）：可编辑、可删除、可确认 */
    public static final String DRAFT = "D";
    /** 已入库：只读，是入库记录里真正生效的单据 */
    public static final String CONFIRMED = "C";
    /** 已作废 */
    public static final String CANCELLED = "X";

    /** 状态 → 文案，用 LinkedHashMap 保证字典接口返回顺序稳定 */
    private static final Map<String, String> TEXT = new LinkedHashMap<>();

    static {
        TEXT.put(DRAFT, "待入库");
        TEXT.put(CONFIRMED, "已入库");
        TEXT.put(CANCELLED, "已作废");
    }

    private InboundStatus() {
    }

    public static boolean isValid(String status) {
        return TEXT.containsKey(status);
    }

    public static String labelOf(String status) {
        return TEXT.getOrDefault(status, status);
    }

    /** 字典项，供前端下拉框使用 */
    public static Map<String, String> dict() {
        return new LinkedHashMap<>(TEXT);
    }
}
