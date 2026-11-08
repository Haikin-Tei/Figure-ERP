package com.figureerp.module.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增 / 编辑物流价格。
 */
@Data
@Schema(description = "物流价格保存参数")
public class LogisticsPriceSaveRequest {

    @Schema(description = "物流供应商编码", example = "SF", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择物流供应商")
    private String supplierCode;

    @Schema(description = "出发地-省", example = "上海市", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择出发地省份")
    private String originProvince;

    @Schema(description = "出发地-市", example = "上海市", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择出发地城市")
    private String originCity;

    @Schema(description = "出发地-区/县", example = "浦东新区")
    private String originDistrict;

    @Schema(description = "目的地-省", example = "广东省", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择目的地省份")
    private String destProvince;

    @Schema(description = "目的地-市，留空表示全省统一价", example = "深圳市")
    private String destCity;

    @Schema(description = "首重重量（kg）", example = "1.00")
    @NotNull(message = "请填写首重重量")
    @DecimalMin(value = "0.1", message = "首重重量不能小于 0.1kg")
    @DecimalMax(value = "100", message = "首重重量不能超过 100kg")
    private BigDecimal firstWeight = new BigDecimal("1.00");

    @Schema(description = "首重价格（元）", example = "12.00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写首重价格")
    @DecimalMin(value = "0", message = "首重价格不能为负数")
    @DecimalMax(value = "99999", message = "首重价格过大")
    private BigDecimal firstPrice;

    @Schema(description = "续重单位（kg）", example = "1.00")
    @NotNull(message = "请填写续重单位")
    @DecimalMin(value = "0.1", message = "续重单位不能小于 0.1kg")
    @DecimalMax(value = "100", message = "续重单位不能超过 100kg")
    private BigDecimal additionalWeight = new BigDecimal("1.00");

    @Schema(description = "续重单价（元）", example = "2.00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写续重单价")
    @DecimalMin(value = "0", message = "续重单价不能为负数")
    @DecimalMax(value = "99999", message = "续重单价过大")
    private BigDecimal additionalPrice;

    @Schema(description = "预计时效（天）", example = "2")
    @Min(value = 1, message = "时效至少 1 天")
    @Max(value = 60, message = "时效不能超过 60 天")
    private Integer estimatedDays;

    @Schema(description = "状态：1 启用，0 停用", example = "1")
    private Integer status = 1;

    @Schema(description = "备注")
    private String remark;
}
