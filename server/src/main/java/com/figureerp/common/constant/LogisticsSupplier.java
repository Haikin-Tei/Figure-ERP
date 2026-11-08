package com.figureerp.common.constant;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 物流供应商。
 * <p>
 * 后三个字段是「上海出发、目的地为华东」的基准价，仅用于首次初始化全国价格，
 * 之后一律以数据库 {@code logistics_price} 表的记录为准，改基准价不会影响已生成的数据。
 * <ul>
 *   <li>baseFirstPrice：1kg 首重价（元）</li>
 *   <li>baseAdditionalPrice：每 1kg 续重价（元）</li>
 *   <li>baseDays：华东地区参考时效（天）</li>
 * </ul>
 */
@Getter
public enum LogisticsSupplier {

    SF("SF", "顺丰速运", "12.00", "2.00", 1),
    JD("JD", "京东物流", "10.00", "2.00", 2),
    ZTO("ZTO", "中通快递", "6.00", "1.50", 2),
    YTO("YTO", "圆通速递", "6.00", "1.50", 2),
    YUNDA("YUNDA", "韵达快递", "5.50", "1.50", 2),
    STO("STO", "申通快递", "5.50", "1.50", 2),
    JTSD("JTSD", "极兔速递", "5.00", "1.50", 3),
    EMS("EMS", "中国邮政EMS", "12.00", "3.00", 2);

    private final String code;
    private final String label;
    private final BigDecimal baseFirstPrice;
    private final BigDecimal baseAdditionalPrice;
    private final int baseDays;

    LogisticsSupplier(String code, String label, String baseFirstPrice, String baseAdditionalPrice, int baseDays) {
        this.code = code;
        this.label = label;
        this.baseFirstPrice = new BigDecimal(baseFirstPrice);
        this.baseAdditionalPrice = new BigDecimal(baseAdditionalPrice);
        this.baseDays = baseDays;
    }

    public static String labelOf(String code) {
        LogisticsSupplier supplier = of(code);
        return supplier == null ? "未知供应商" : supplier.label;
    }

    public static LogisticsSupplier of(String code) {
        for (LogisticsSupplier supplier : values()) {
            if (supplier.code.equals(code)) {
                return supplier;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return of(code) != null;
    }
}
