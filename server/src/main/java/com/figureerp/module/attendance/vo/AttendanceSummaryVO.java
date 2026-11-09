package com.figureerp.module.attendance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 员工考勤页面顶部统计卡。
 * <p>
 * 口径统一按「本月」：考勤日期落在当前自然月、未删除的记录。
 * 执行级账号看到的是自己的数字（与列表口径一致），主管 / 人事看到全公司的。
 */
@Data
@Schema(description = "员工考勤统计")
public class AttendanceSummaryVO {

    @Schema(description = "本月考勤记录数（人 × 天）")
    private Long monthRecords;

    @Schema(description = "本月迟到次数")
    private Long lateCount;

    @Schema(description = "本月早退次数")
    private Long earlyCount;

    @Schema(description = "本月未打下班卡次数")
    private Long missingCount;

    @Schema(description = "本月有考勤记录的员工数")
    private Long staffCount;
}
