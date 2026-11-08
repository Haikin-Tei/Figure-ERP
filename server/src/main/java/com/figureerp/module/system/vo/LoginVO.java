package com.figureerp.module.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 登录成功返回：令牌 + 当前用户信息。
 * <p>
 * 一次把用户信息带回去，前端登录后不用再多发一次 /auth/me 请求。
 */
@Data
@Builder
@Schema(description = "登录结果")
public class LoginVO {

    @Schema(description = "JWT 令牌，后续请求放在 Authorization: Bearer {token}")
    private String token;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "有效期（秒）")
    private long expiresIn;

    @Schema(description = "登录用户信息")
    private UserVO user;
}
