package com.figureerp.module.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码。
 */
@Data
@Schema(description = "重置密码参数")
public class PasswordResetRequest {

    @Schema(description = "新密码", example = "123456")
    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 个字符之间")
    private String newPassword;
}
