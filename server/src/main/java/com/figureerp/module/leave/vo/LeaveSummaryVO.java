package com.figureerp.module.leave.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 请假申请页面顶部统计卡。
 * <p>
 * 待审批是全量（不受月份限制），保证待办不会被月份切掉；
 * 其余按「请假开始时间落在本月」统计。
 * 执行级账号看到的是自己的数字，主管 / 人事看到全公司的。
 */
@Data
@Schema(description = "请假申请统计")
public class LeaveSummaryVO {

    @Schema(description = "待审批单数（全量）")
    private Long pendingCount;

    @Schema(description = "本月已通过的请假单数")
    private Long monthApprovedCount;

    @Schema(description = "本月已通过的请假天数合计")
    private BigDecimal monthDays;

    @Schema(description = "本月请假涉及的人数")
    private Long monthStaffCount;
}
