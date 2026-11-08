package com.figureerp.module.stock.converter;

/**
 * 区域状态常量（与数据库 char(1) 取值一一对应）。
 * <p>
 * 只有「使用中 / 停用」两态，比货架的 A/B/D 少一个「维护中」。
 */
public final class AreaStatus {

    /** 使用中 */
    public static final String IN_USE = "A";
    /** 停用 */
    public static final String DISABLED = "D";

    private AreaStatus() {
    }

    public static boolean isValid(String status) {
        return IN_USE.equals(status) || DISABLED.equals(status);
    }
}
