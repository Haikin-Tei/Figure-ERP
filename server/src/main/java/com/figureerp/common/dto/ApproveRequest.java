package com.figureerp.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批入参（请假 / 加班共用）。
 * <p>
 * 只有两个动作：通过（A）或驳回（R）。撤销是申请人的动作，走各自的 /cancel 接口，
 * 不放进来，避免「审批人替人撤销」这种越权路径。
 */
@Data
@Schema(description = "审批参数")
public class ApproveRequest {

    @Schema(description = "审批结果：A 通过 / R 驳回", requiredMode = Schema.RequiredMode.REQUIRED, example = "A")
    @NotBlank(message = "请选择审批结果")
    @Pattern(regexp = "^[AR]$", message = "审批结果只能是 A（通过）或 R（驳回）")
    private String result;

    @Schema(description = "审批意见 / 驳回原因，建议驳回时填写")
    @Size(max = 255, message = "审批意见不能超过 255 个字符")
    private String remark;
}
