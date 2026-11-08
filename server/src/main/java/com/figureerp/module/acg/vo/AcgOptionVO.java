package com.figureerp.module.acg.vo;

/**
 * 下拉选项：其他业务表（手办商品、订单等）选作品时用的精简结构。
 * <p>
 * 只返回启用（status=1）的作品，带热度便于选品时参考，但不带销量等敏感/冗余字段。
 */
public record AcgOptionVO(
        Long id,
        String name,
        String workType,
        String workTypeText,
        Integer releaseYear,
        Integer heatScore
) {
}
