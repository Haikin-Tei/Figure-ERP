package com.figureerp.module.stock.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存列表出参：一行 = 一款手办的库存汇总（可能散布在多个库位）。
 * <p>
 * 商品还在时，名称 / 货号 / 品牌 / 图片以商品档案的当前值为准（改名后自动同步）；
 * 商品已被逻辑删除时退回入库时的快照，并置 {@code productDeleted=true}，前端标「已下架」。
 */
@Data
@Schema(description = "库存（按手办汇总）")
public class StockVO {

    @Schema(description = "手办商品 ID")
    private Long productId;

    @Schema(description = "手办名称")
    private String productName;

    @Schema(description = "货号 / SKU")
    private String productCode;

    @Schema(description = "品牌名称")
    private String brand;

    @Schema(description = "首图 URL，商品已删除时为空")
    private String imageUrl;

    @Schema(description = "商品是否已下架（逻辑删除）")
    private Boolean productDeleted;

    @Schema(description = "商品状态：1 在售 / 0 停用；已下架时为 null")
    private Integer productStatus;

    @Schema(description = "商品状态文案：在售 / 已停用 / 已下架")
    private String productStatusText;

    @Schema(description = "在库件数合计")
    private Integer totalQuantity;

    @Schema(description = "库存金额合计 = Σ 数量 × 加权平均成本")
    private BigDecimal totalAmount;

    @Schema(description = "加权平均成本单价 = 金额合计 / 件数合计，仅供展示")
    private BigDecimal avgCost;

    @Schema(description = "分布在几个库位（仓库 + 区域 + 货架 组合数）")
    private Integer locationCount;

    @Schema(description = "最近一次入库时间")
    private LocalDateTime lastInboundTime;
}
