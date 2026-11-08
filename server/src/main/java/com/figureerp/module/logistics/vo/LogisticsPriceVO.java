package com.figureerp.module.logistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物流价格出参（列表 / 详情共用）。
 */
@Data
@Schema(description = "物流价格")
public class LogisticsPriceVO {

    private Long id;

    @Schema(description = "供应商编码")
    private String supplierCode;

    @Schema(description = "供应商名称")
    private String supplierName;

    private String originProvince;
    private String originCity;
    private String originDistrict;

    @Schema(description = "出发地展示文案，如「上海市 浦东新区」")
    private String originLabel;

    private String destProvince;

    @Schema(description = "目的地城市，为空表示全省")
    private String destCity;

    @Schema(description = "目的地展示文案，如「广东省 深圳市」或「广东省（全省）」")
    private String destLabel;

    @Schema(description = "所属运费区域，如「华南」")
    private String zoneLabel;

    @Schema(description = "首重重量（kg）")
    private BigDecimal firstWeight;

    @Schema(description = "首重价格（元）")
    private BigDecimal firstPrice;

    @Schema(description = "续重单位（kg）")
    private BigDecimal additionalWeight;

    @Schema(description = "续重单价（元）")
    private BigDecimal additionalPrice;

    @Schema(description = "计价公式说明，如「首重 1kg 12 元，续重每 1kg 2 元」")
    private String priceLabel;

    @Schema(description = "预计时效（天）")
    private Integer estimatedDays;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
