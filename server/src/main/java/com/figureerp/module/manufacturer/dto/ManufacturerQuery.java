package com.figureerp.module.manufacturer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 厂商列表查询参数。
 * <p>
 * {@code keyword} 模糊匹配公司名称 / 国家地区，{@code country}、{@code status} 为精确匹配。
 */
@Data
public class ManufacturerQuery {

    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 100, message = "每页最多 100 条")
    private Integer size = 10;

    /** 关键字，模糊匹配公司名称 / 国家地区 */
    private String keyword;

    /** 国家 / 地区，精确匹配 */
    private String country;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 排序：createDesc（默认，最新创建在前）/ nameAsc / foundedAsc / foundedDesc */
    private String sortBy = "createDesc";
}
