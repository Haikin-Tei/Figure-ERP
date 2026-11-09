package com.figureerp.module.leave.converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请假类型常量（与数据库 char(1) 取值一一对应）。
 * <p>
 * 新增类型时在这里加一行即可：字典接口、前端下拉、文案展示都会自动带上，
 * 不用改表（字段是 varchar / char）也不用改业务代码。
 */
public final class LeaveType {

    public static final String PERSONAL = "A";
    public static final String SICK = "B";
    public static final String ANNUAL = "C";
    public static final String COMPENSATORY = "D";
    public static final String MARRIAGE = "E";
    public static final String MATERNITY = "F";
    public static final String OTHER = "G";

    private static final Map<String, String> TEXT = new LinkedHashMap<>();

    static {
        TEXT.put(PERSONAL, "事假");
        TEXT.put(SICK, "病假");
        TEXT.put(ANNUAL, "年假");
        TEXT.put(COMPENSATORY, "调休");
        TEXT.put(MARRIAGE, "婚假");
        TEXT.put(MATERNITY, "产假");
        TEXT.put(OTHER, "其他");
    }

    private LeaveType() {
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
