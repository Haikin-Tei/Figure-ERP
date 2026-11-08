package com.figureerp.module.message.converter;

import java.util.List;
import java.util.Map;

/**
 * 消息的类型 / 状态 / 优先级字典。
 * <p>
 * 文案集中在这里翻译，前端直接展示 VO 里的 xxxText，不用各页面各写一套映射。
 * <p>
 * 扩展方式：以后要加「合同」「退款」等新消息种类，只在 TYPE_TEXT 里追加一项即可，
 * 数据库 msg_type 是 varchar 不需要改表，前端下拉通过 {@code GET /api/message/types} 拿。
 */
public final class MessageDict {

    /* ---------------- 消息类型 ---------------- */

    public static final String TYPE_APPROVAL = "APPROVAL";
    public static final String TYPE_ORDER = "ORDER";
    public static final String TYPE_INVENTORY = "INVENTORY";
    public static final String TYPE_SYSTEM = "SYSTEM";

    /**
     * 顺序固定的一份类型列表，供字典接口与文案翻译使用。
     * <p>
     * 不用 Map 而用 List&lt;Entry&gt;：Map.of 的迭代顺序是不确定的，
     * 而前端下拉需要「审批 → 订单 → 库存 → 系统」这个固定顺序。
     */
    private static final List<Map.Entry<String, String>> TYPE_ENTRIES = List.of(
            Map.entry(TYPE_APPROVAL, "审批消息"),
            Map.entry(TYPE_ORDER, "订单消息"),
            Map.entry(TYPE_INVENTORY, "库存消息"),
            Map.entry(TYPE_SYSTEM, "系统消息")
    );

    /* ---------------- 状态 ---------------- */

    public static final String STATUS_UNREAD = "U";
    public static final String STATUS_READ = "R";
    public static final String STATUS_HANDLED = "H";

    private static final Map<String, String> STATUS_TEXT = Map.of(
            STATUS_UNREAD, "未读",
            STATUS_READ, "已读",
            STATUS_HANDLED, "已处理"
    );

    /* ---------------- 优先级 ---------------- */

    public static final String PRIORITY_NORMAL = "N";
    public static final String PRIORITY_URGENT = "U";

    private static final Map<String, String> PRIORITY_TEXT = Map.of(
            PRIORITY_NORMAL, "普通",
            PRIORITY_URGENT, "紧急"
    );

    private MessageDict() {
    }

    public static List<Map.Entry<String, String>> typeEntries() {
        return TYPE_ENTRIES;
    }

    public static boolean isValidType(String type) {
        return type != null && TYPE_ENTRIES.stream().anyMatch(e -> e.getKey().equals(type));
    }

    public static boolean isValidStatus(String status) {
        return STATUS_TEXT.containsKey(status);
    }

    public static boolean isValidPriority(String priority) {
        return PRIORITY_TEXT.containsKey(priority);
    }

    public static String typeText(String type) {
        return TYPE_ENTRIES.stream()
                .filter(e -> e.getKey().equals(type))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(type);
    }

    public static String statusText(String status) {
        return STATUS_TEXT.getOrDefault(status, status);
    }

    public static String priorityText(String priority) {
        return PRIORITY_TEXT.getOrDefault(priority, priority);
    }
}
