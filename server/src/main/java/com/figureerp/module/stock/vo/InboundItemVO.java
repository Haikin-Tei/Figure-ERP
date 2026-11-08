package com.figureerp.module.stock.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 入库单明细出参。
 */
@Data
@Schema(description = "入库单明细")
public class InboundItemVO {

    private Long id;

    @Schema(description = "手办商品 ID")
    private Long productId;

    @Schema(description = "手办名称（入库时的快照）")
    private String productName;

    @Schema(description = "货号（入库时的快照）")
    private String productCode;

    @Schema(description = "入库数量")
    private Integer quantity;

    @Schema(description = "单价（元）")
    private BigDecimal unitPrice;

    @Schema(description = "小计金额 = 数量 × 单价")
    private BigDecimal amount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "排序号")
    private Integer sortNo;
}
