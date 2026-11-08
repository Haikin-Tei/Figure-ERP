package com.figureerp.module.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 运费报价查询：给定「出发地 + 目的地 + 重量」，返回各供应商的价格对比。
 */
@Data
@Schema(description = "运费报价查询条件")
public class QuoteQuery {

    @Schema(description = "出发地-省", example = "上海市")
    private String originProvince = "上海市";

    @Schema(description = "出发地-市", example = "上海市")
    private String originCity = "上海市";

    @Schema(description = "出发地-区/县", example = "浦东新区")
    private String originDistrict = "浦东新区";

    @Schema(description = "目的地-省", example = "广东省", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择目的地省份")
    private String destProvince;

    @Schema(description = "目的地-市，留空则查该省统一价", example = "深圳市")
    private String destCity;

    @Schema(description = "重量（kg）", example = "2.5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写重量")
    @DecimalMin(value = "0.1", message = "重量不能小于 0.1kg")
    @DecimalMax(value = "500", message = "单票重量不能超过 500kg")
    private BigDecimal weight = BigDecimal.ONE;
}
