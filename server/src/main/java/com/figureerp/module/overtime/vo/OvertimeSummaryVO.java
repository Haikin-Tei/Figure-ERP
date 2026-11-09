package com.figureerp.module.overtime.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 加班申请页面顶部统计卡。
 * <p>
 * 待审批是全量（不受月份限制），保证待办不会被月份切掉；
 * 其余按「加班开始时间落在本月」统计。
 */
@Data
@Schema(description = "加班申请统计")
public class OvertimeSummaryVO {

    @Schema(description = "待审批单数（全量）")
    private Long pendingCount;

    @Schema(description = "本月已通过的加班单数")
    private Long monthApprovedCount;

    @Schema(description = "本月已通过的加班时长合计（小时）")
    private BigDecimal monthHours;

    @Schema(description = "本月加班涉及的人数")
    private Long monthStaffCount;
}
