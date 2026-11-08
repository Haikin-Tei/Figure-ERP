package com.figureerp.module.stock.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 入库记录页面顶部的统计卡。
 * <p>
 * 本月口径按 inbound_time 落在当前自然月、且状态为「已入库」的单据统计，
 * 待入库单数则是全量（不受月份限制），保证待办不会被月份切掉。
 */
@Data
@Schema(description = "入库记录统计")
public class InboundSummaryVO {

    @Schema(description = "待入库单数（全量）")
    private Long pendingCount;

    @Schema(description = "本月已入库单数")
    private Long monthOrderCount;

    @Schema(description = "本月已入库件数（明细数量合计）")
    private Long monthQuantity;

    @Schema(description = "本月已入库金额")
    private BigDecimal monthAmount;
}
