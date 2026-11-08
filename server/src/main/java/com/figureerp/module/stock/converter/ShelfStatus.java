package com.figureerp.module.stock.converter;

/**
 * 货架状态常量（与数据库 char(1) 取值一一对应）。
 * <p>
 * 注意与 {@link WarehouseStatus} 区分：仓库的「盘库中」是 C，货架的「维护中」是 B，
 * 两套状态各自独立，前端下拉不要混用。
 */
public final class ShelfStatus {

    /** 正常 */
    public static final String NORMAL = "A";
    /** 维护中 */
    public static final String MAINTAINING = "B";
    /** 停用 */
    public static final String DISABLED = "D";

    private ShelfStatus() {
    }

    public static boolean isValid(String status) {
        return NORMAL.equals(status) || MAINTAINING.equals(status) || DISABLED.equals(status);
    }
}
