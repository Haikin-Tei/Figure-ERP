package com.figureerp.module.manufacturer.vo;

/**
 * 下拉选项：其他业务表（手办商品、入库等）选厂商时用的精简结构。
 * <p>
 * 只返回启用（status=1）的厂商，带 Logo 便于前端展示。
 */
public record ManufacturerOptionVO(
        Long id,
        String name,
        String logo,
        String country
) {
}
