package com.figureerp.common.constant;

import lombok.Getter;

/**
 * 运费区域。
 * <p>
 * 快递公司内部一般按「区域」定价，而不是 34 个省级行政区各一套价。
 * 这里把全国划成 10 个区域，区域之间的差价体现在三个偏移量上，
 * 由 {@code LogisticsDataInitializer} 在生成初始价格时叠加到供应商基准价上。
 * <p>
 * 偏移量只是「初始参考值」，生成后每条记录都可以在页面上单独改。
 */
@Getter
public enum FreightZone {

    EAST("华东", 0, 0, 0),
    NORTH("华北", 2, 1, 1),
    CENTRAL("华中", 2, 1, 1),
    SOUTH("华南", 2, 1, 1),
    NORTHEAST("东北", 4, 2, 2),
    SOUTHWEST("西南", 4, 2, 2),
    NORTHWEST("西北", 6, 3, 3),
    XINJIANG("新疆", 10, 5, 4),
    TIBET("西藏", 13, 8, 5),
    HMT("港澳台", 20, 10, 4);

    /** 区域名称 */
    private final String label;
    /** 首重加价（元） */
    private final int firstOffset;
    /** 续重加价（元/kg） */
    private final int additionalOffset;
    /** 时效增加（天） */
    private final int daysOffset;

    FreightZone(String label, int firstOffset, int additionalOffset, int daysOffset) {
        this.label = label;
        this.firstOffset = firstOffset;
        this.additionalOffset = additionalOffset;
        this.daysOffset = daysOffset;
    }
}
