package com.figureerp.module.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 员工考勤列表查询条件。
 * <p>
 * keyword 匹配员工姓名 / 账号；userId 用于主管按某个人过滤。
 * 执行级账号（人事岗除外）看列表时后端会强制加上「只看自己」的条件，传了 userId 也不生效。
 */
@Data
@Schema(description = "员工考勤查询条件")
public class AttendanceQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private long page = 1;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 500, message = "每页条数不能超过 500")
    private long size = 10;

    @Schema(description = "关键字，匹配员工姓名 / 登录账号", example = "张三")
    private String keyword;

    @Schema(description = "员工 ID，主管按人筛选时使用")
    private Long userId;

    @Schema(description = "上班状态：N 正常 / L 迟到")
    private String checkInStatus;

    @Schema(description = "下班状态：N 正常 / E 早退 / M 未打卡 / X 免打卡")
    private String checkOutStatus;

    @Schema(description = "考勤日期起（含当天）", example = "2026-11-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @Schema(description = "考勤日期止（含当天）", example = "2026-11-30")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
