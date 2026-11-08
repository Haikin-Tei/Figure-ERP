package com.figureerp.module.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 处理消息时填写的结果说明（可空：直接点「去处理」不填也能提交）。
 */
@Data
@Schema(description = "消息处理参数")
public class MessageHandleRequest {

    @Schema(description = "处理备注 / 处理结果，最多 255 字", example = "已核对库存，同意入库")
    @Size(max = 255, message = "处理备注不能超过 255 个字符")
    private String remark;
}
