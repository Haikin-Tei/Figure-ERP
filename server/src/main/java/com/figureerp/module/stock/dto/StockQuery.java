package com.figureerp.module.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 库存（手办列表）查询条件。
 * <p>
 * 列表按手办聚合，所以筛选条件作用在聚合前的行上：
 * 选了仓库就只汇总该仓库的库存，关键字匹配手办名称 / 货号。
 */
@Data
@Schema(description = "库存查询条件")
public class StockQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer page = 1;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 200, message = "每页条数不能超过 200")
    private Integer size = 10;

    @Schema(description = "关键字，匹配手办名称 / 货号")
    private String keyword;

    @Schema(description = "只看某个仓库的库存，warehouse.id")
    private Long warehouseId;

    @Schema(description = "排序：QUANTITY 库存件数降序（默认）/ AMOUNT 库存金额降序 / RECENT 最近入库优先 / NAME 名称升序")
    private String sortBy;
}
