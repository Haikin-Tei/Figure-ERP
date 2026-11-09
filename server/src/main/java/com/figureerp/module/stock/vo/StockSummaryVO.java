package com.figureerp.module.stock.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 手办列表顶部的统计卡。
 */
@Data
@Schema(description = "库存统计")
public class StockSummaryVO {

    @Schema(description = "在库款式数（去重后的手办数）")
    private Long skuCount;

    @Schema(description = "在库总件数")
    private Long totalQuantity;

    @Schema(description = "库存总金额")
    private BigDecimal totalAmount;

    @Schema(description = "涉及仓库数")
    private Long warehouseCount;

    @Schema(description = "占用库位数（仓库 + 区域 + 货架 组合数）")
    private Long locationCount;
}
