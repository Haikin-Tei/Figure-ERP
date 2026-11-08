package com.figureerp.module.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 物流价格列表查询条件。
 */
@Data
@Schema(description = "物流价格查询条件")
public class LogisticsPriceQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private long page = 1;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 500, message = "每页条数不能超过 500")
    private long size = 10;

    @Schema(description = "物流供应商编码：SF / JD / ZTO / YTO / YUNDA / STO / JTSD / EMS")
    private String supplierCode;

    @Schema(description = "出发地-省")
    private String originProvince;

    @Schema(description = "出发地-市")
    private String originCity;

    @Schema(description = "目的地-省")
    private String destProvince;

    @Schema(description = "目的地-市")
    private String destCity;

    @Schema(description = "关键字，匹配目的地省份或城市")
    private String keyword;

    @Schema(description = "状态：1 启用，0 停用")
    private Integer status;
}
