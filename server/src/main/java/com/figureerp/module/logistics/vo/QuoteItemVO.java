package com.figureerp.module.logistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单个供应商的报价结果。
 */
@Data
@Schema(description = "供应商报价")
public class QuoteItemVO {

    private String supplierCode;
    private String supplierName;

    @Schema(description = "总价（元）")
    private BigDecimal totalPrice;

    @Schema(description = "首重重量（kg）")
    private BigDecimal firstWeight;

    @Schema(description = "首重价格（元）")
    private BigDecimal firstPrice;

    @Schema(description = "续重单位（kg）")
    private BigDecimal additionalWeight;

    @Schema(description = "续重单价（元）")
    private BigDecimal additionalPrice;

    @Schema(description = "超出的计费档数，如 2 表示按 2 个续重单位收费")
    private BigDecimal additionalUnits;

    @Schema(description = "计价明细，如「首重 1kg 12.00 元 + 续重 2×1kg × 2.00 元」")
    private String formula;

    @Schema(description = "预计时效（天）")
    private Integer estimatedDays;

    @Schema(description = "是否最低价")
    private boolean cheapest;

    @Schema(description = "是否最快")
    private boolean fastest;

    @Schema(description = "备注")
    private String remark;
}
