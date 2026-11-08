package com.figureerp.module.manufacturer.vo;

/**
 * 厂商目录统计卡（列表页顶部）：总数 / 启用 / 停用 / 覆盖国家地区数。
 */
public record ManufacturerSummaryVO(
        long total,
        long enabled,
        long disabled,
        long countryCount
) {
}
