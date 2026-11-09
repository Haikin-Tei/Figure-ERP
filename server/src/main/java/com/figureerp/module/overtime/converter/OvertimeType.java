package com.figureerp.module.overtime.converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 加班类型常量（与数据库 char(1) 取值一一对应）。
 * <p>
 * 区分工作日 / 休息日 / 节假日是为了后续能按不同倍率核算加班费，
 * 当前只做记录与审批，不参与薪资计算。
 */
public final class OvertimeType {

    public static final String WEEKDAY = "W";
    public static final String REST_DAY = "O";
    public static final String HOLIDAY = "H";

    private static final Map<String, String> TEXT = new LinkedHashMap<>();

    static {
        TEXT.put(WEEKDAY, "工作日加班");
        TEXT.put(REST_DAY, "休息日加班");
        TEXT.put(HOLIDAY, "节假日加班");
    }

    private OvertimeType() {
    }

    public static boolean isValid(String code) {
        return TEXT.containsKey(code);
    }

    public static String labelOf(String code) {
        return TEXT.getOrDefault(code, code);
    }

    public static Map<String, String> dict() {
        return new LinkedHashMap<>(TEXT);
    }
}
