package com.figureerp.module.stock.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存库位明细：某个手办在某个仓库 / 货架上具体有多少件。
 * <p>
 * 仓库、货架被删除时只留 id，名称字段为空，前端展示「已删除」。
 */
@Data
@Schema(description = "库存库位明细")
public class StockLocationVO {

    @Schema(description = "库存行 ID，stock.id")
    private Long id;

    @Schema(description = "仓库 ID")
    private Long warehouseId;

    @Schema(description = "仓库代码")
    private String warehouseCode;

    @Schema(description = "仓库名称")
    private String warehouseName;

    @Schema(description = "货架 ID，可空")
    private Long shelfId;

    @Schema(description = "货架编号，可空")
    private String shelfCode;

    @Schema(description = "货架库位描述，如 01区-1层")
    private String shelfLocation;

    @Schema(description = "该库位在库件数")
    private Integer quantity;

    @Schema(description = "该库位加权平均成本单价")
    private BigDecimal avgCost;

    @Schema(description = "该库位库存金额 = 数量 × 平均成本")
    private BigDecimal amount;

    @Schema(description = "该库位最近入库时间")
    private LocalDateTime lastInboundTime;
}
