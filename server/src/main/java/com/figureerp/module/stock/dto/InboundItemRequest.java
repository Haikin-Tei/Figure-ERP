package com.figureerp.module.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 入库单明细的提交参数。
 * <p>
 * amount 不由前端传，服务层按 quantity × unitPrice 计算；
 * 同一张单里同一款手办只能出现一次（重复提交会返回 1003 拦住）。
 */
@Data
@Schema(description = "入库单明细保存参数")
public class InboundItemRequest {

    @Schema(description = "手办商品 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择入库手办")
    private Long productId;

    @Schema(description = "入库数量，必须大于 0", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写入库数量")
    @Min(value = 1, message = "入库数量必须大于 0")
    private Integer quantity;

    @Schema(description = "单价（元），可空表示不记金额", example = "399.00")
    @DecimalMin(value = "0", message = "单价不能小于 0")
    private BigDecimal unitPrice;

    @Schema(description = "备注")
    @Size(max = 255, message = "明细备注不能超过 255 个字符")
    private String remark;
}
