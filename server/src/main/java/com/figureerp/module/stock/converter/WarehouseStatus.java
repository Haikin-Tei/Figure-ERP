package com.figureerp.module.stock.converter;

/**
 * 仓库状态与类型常量（与数据库 char(1) 取值一一对应）。
 */
public final class WarehouseStatus {

    /** 使用中 */
    public static final String IN_USE = "A";
    /** 盘库中 */
    public static final String STOCKTAKING = "C";
    /** 停用中 */
    public static final String DISABLED = "D";

    /** 正常仓 */
    public static final String NORMAL = "N";
    /** 退货仓 */
    public static final String RETURN = "Y";

    private WarehouseStatus() {
    }

    public static boolean isValidStatus(String status) {
        return IN_USE.equals(status) || STOCKTAKING.equals(status) || DISABLED.equals(status);
    }

    public static boolean isValidReturn(String isReturn) {
        return NORMAL.equals(isReturn) || RETURN.equals(isReturn);
    }
}
