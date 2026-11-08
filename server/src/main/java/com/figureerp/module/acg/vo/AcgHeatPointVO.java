package com.figureerp.module.acg.vo;

import java.time.LocalDate;

/**
 * 热度趋势上的一个点。
 */
public record AcgHeatPointVO(
        LocalDate recordDate,
        Integer heatScore,
        Integer monthlySales,
        String remark
) {
}
