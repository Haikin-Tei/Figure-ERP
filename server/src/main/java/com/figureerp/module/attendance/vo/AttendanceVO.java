package com.figureerp.module.attendance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤记录出参。
 * <p>
 * 状态文案、工时、是否免下班卡都由后端算好，前端只负责展示，避免前后端各写一套判定规则。
 */
@Data
@Schema(description = "员工考勤记录")
public class AttendanceVO {

    private Long id;

    @Schema(description = "员工 ID")
    private Long userId;

    @Schema(description = "员工姓名")
    private String userName;

    @Schema(description = "角色编码")
    private String role;

    @Schema(description = "角色文案，如「销售专员」")
    private String roleLabel;

    @Schema(description = "考勤日期")
    private LocalDate attendDate;

    @Schema(description = "上班时间")
    private LocalDateTime checkInTime;

    @Schema(description = "下班时间，销售岗为空")
    private LocalDateTime checkOutTime;

    @Schema(description = "上班状态：N 正常 / L 迟到")
    private String checkInStatus;

    @Schema(description = "上班状态文案")
    private String checkInStatusText;

    @Schema(description = "下班状态：N 正常 / E 早退 / M 未打卡 / X 免打卡")
    private String checkOutStatus;

    @Schema(description = "下班状态文案")
    private String checkOutStatusText;

    @Schema(description = "当日工时（分钟）")
    private Integer workMinutes;

    @Schema(description = "当日工时（小时，保留 1 位小数），供前端直接展示")
    private BigDecimal workHours;

    @Schema(description = "是否销售岗免下班卡")
    private Boolean exemptCheckOut;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
