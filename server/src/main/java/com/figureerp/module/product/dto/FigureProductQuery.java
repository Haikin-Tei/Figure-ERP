package com.figureerp.module.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 手办商品列表查询条件（分页 + 多字段模糊）。
 */
@Data
@Schema(description = "手办商品查询条件")
public class FigureProductQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private long page = 1;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 500, message = "每页条数不能超过 500")
    private long size = 10;

    @Schema(description = "商品名称（模糊）")
    private String name;

    @Schema(description = "货号 / SKU（模糊）")
    private String code;

    @Schema(description = "品牌（模糊）")
    private String brand;

    @Schema(description = "系列（模糊）")
    private String series;

    @Schema(description = "角色名（模糊）")
    private String characterName;

    @Schema(description = "状态：1 启用，0 停用")
    private Integer status;
}
