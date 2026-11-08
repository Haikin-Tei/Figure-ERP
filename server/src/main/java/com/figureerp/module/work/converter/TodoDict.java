package com.figureerp.module.work.converter;

import java.util.List;
import java.util.Map;

/**
 * 待办的优先级 / 状态字典。
 * <p>
 * 文案集中在这里翻译，前端直接展示 VO 里的 xxxText，不用各页面各写一套映射。
 * 数据库存的是 char(1)，加新优先级只要在这里补一项，不用改表。
 */
public final class TodoDict {

    /* ---------------- 优先级 ---------------- */

    public static final String PRIORITY_HIGH = "H";
    public static final String PRIORITY_NORMAL = "N";
    public static final String PRIORITY_LOW = "L";

    /**
     * 固定顺序的优先级列表：字典接口与前端下拉都按这个顺序渲染。
     * 用 List 而不是 Map，避免 Map.of 的迭代顺序不确定导致下拉顺序乱跳。
     */
    private static final List<Map.Entry<String, String>> PRIORITY_ENTRIES = List.of(
            Map.entry(PRIORITY_HIGH, "高"),
            Map.entry(PRIORITY_NORMAL, "普通"),
            Map.entry(PRIORITY_LOW, "低")
    );

    private static final Map<String, String> PRIORITY_TEXT = Map.of(
            PRIORITY_HIGH, "高",
            PRIORITY_NORMAL, "普通",
            PRIORITY_LOW, "低"
    );

    /* ---------------- 状态 ---------------- */

    public static final String STATUS_PENDING = "P";
    public static final String STATUS_DONE = "D";

    private static final Map<String, String> STATUS_TEXT = Map.of(
            STATUS_PENDING, "待办",
            STATUS_DONE, "已完成"
    );

    private TodoDict() {
    }

    public static List<Map.Entry<String, String>> priorityEntries() {
        return PRIORITY_ENTRIES;
    }

    public static boolean isValidPriority(String code) {
        return code != null && PRIORITY_TEXT.containsKey(code);
    }

    public static boolean isValidStatus(String code) {
        return code != null && STATUS_TEXT.containsKey(code);
    }

    public static String priorityText(String code) {
        return PRIORITY_TEXT.getOrDefault(code, "普通");
    }

    public static String statusText(String code) {
        return STATUS_TEXT.getOrDefault(code, "待办");
    }

    /**
     * 优先级权重，用于「高 → 普通 → 低」排序；未知值按普通处理。
     */
    public static int priorityWeight(String code) {
        return switch (code == null ? PRIORITY_NORMAL : code) {
            case PRIORITY_HIGH -> 0;
            case PRIORITY_LOW -> 2;
            default -> 1;
        };
    }
}
