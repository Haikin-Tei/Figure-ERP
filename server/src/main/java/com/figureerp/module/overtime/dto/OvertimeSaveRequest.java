package com.figureerp.module.overtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提交 / 编辑加班申请。
 * <p>
 * 时长由后端按起止时间算（保留 1 位小数），单号由后端生成，状态一律从「待审批」开始。
 * 允许跨天（如 22:00 到次日 02:00），单次上限 24 小时，防手滑填错。
 */
@Data
@Schema(description = "加班申请保存参数")
public class OvertimeSaveRequest {

    @Schema(description = "申请人 ID，留空表示当前登录用户；只有主管 / 人事可以代提")
    private Long userId;

    @Schema(description = "加班类型：W 工作日 / O 休息日 / H 节假日",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "W")
    @NotBlank(message = "请选择加班类型")
    @Pattern(regexp = "^[WOH]$", message = "加班类型只能是 W / O / H")
    private String overtimeType;

    @Schema(description = "加班开始时间", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "2026-11-09T18:30:00")
    @NotNull(message = "请选择加班开始时间")
    private LocalDateTime startTime;

    @Schema(description = "加班结束时间", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "2026-11-09T21:30:00")
    @NotNull(message = "请选择加班结束时间")
    private LocalDateTime endTime;

    @Schema(description = "加班事由，必填", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "双十一大促订单打包")
    @NotBlank(message = "请填写加班事由")
    @Size(max = 500, message = "加班事由不能超过 500 个字符")
    private String reason;
}
