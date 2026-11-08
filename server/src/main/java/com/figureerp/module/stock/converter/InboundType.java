package com.figureerp.module.stock.converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 入库类型常量（与数据库 char(1) 取值一一对应）。
 * <p>
 * 采购 / 退货 / 调拨 / 其他：用于入库记录页面的类型筛选和统计口径区分。
 */
public final class InboundType {

    public static final String PURCHASE = "P";
    public static final String RETURN = "R";
    public static final String TRANSFER = "T";
    public static final String OTHER = "O";

    private static final Map<String, String> TEXT = new LinkedHashMap<>();

    static {
        TEXT.put(PURCHASE, "采购入库");
        TEXT.put(RETURN, "退货入库");
        TEXT.put(TRANSFER, "调拨入库");
        TEXT.put(OTHER, "其他入库");
    }

    private InboundType() {
    }

    public static boolean isValid(String type) {
        return TEXT.containsKey(type);
    }

    public static String labelOf(String type) {
        return TEXT.getOrDefault(type, type);
    }

    public static Map<String, String> dict() {
        return new LinkedHashMap<>(TEXT);
    }
}
