package com.figureerp.module.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求参数。
 */
@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "登录账号", example = "admin")
    @NotBlank(message = "请输入登录账号")
    @Size(max = 50, message = "账号长度不能超过 50 个字符")
    private String username;

    @Schema(description = "登录密码", example = "admin123")
    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 个字符之间")
    private String password;
}
