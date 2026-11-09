package com.figureerp.module.leave.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提交 / 编辑请假申请。
 * <p>
 * 天数由后端按起止时间算（跨自然日按含首尾的天数，同一天按时长记 0.5 / 1 天），
 * 单号由后端生成，状态一律从「待审批」开始。
 */
@Data
@Schema(description = "请假申请保存参数")
public class LeaveSaveRequest {

    @Schema(description = "申请人 ID，留空表示当前登录用户；只有主管 / 人事可以代提")
    private Long userId;

    @Schema(description = "请假类型：A 事假 / B 病假 / C 年假 / D 调休 / E 婚假 / F 产假 / G 其他",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "A")
    @NotBlank(message = "请选择请假类型")
    @Pattern(regexp = "^[A-G]$", message = "请假类型只能是 A~G")
    private String leaveType;

    @Schema(description = "请假开始时间", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "2026-11-10T09:00:00")
    @NotNull(message = "请选择请假开始时间")
    private LocalDateTime startTime;

    @Schema(description = "请假结束时间", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "2026-11-11T18:00:00")
    @NotNull(message = "请选择请假结束时间")
    private LocalDateTime endTime;

    @Schema(description = "请假事由，必填", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "家中有事需要处理")
    @NotBlank(message = "请填写请假事由")
    @Size(max = 500, message = "请假事由不能超过 500 个字符")
    private String reason;
}
