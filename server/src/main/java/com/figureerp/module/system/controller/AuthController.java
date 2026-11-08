package com.figureerp.module.system.controller;

import com.figureerp.common.result.Result;
import com.figureerp.module.system.dto.LoginRequest;
import com.figureerp.module.system.service.AuthService;
import com.figureerp.module.system.vo.LoginVO;
import com.figureerp.module.system.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@Tag(name = "认证", description = "登录、当前用户、退出")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录", description = "账号密码登录，成功后返回 JWT 令牌与用户信息")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", authService.login(request));
    }

    @Operation(summary = "当前登录用户", description = "根据请求头里的令牌返回用户信息，用于刷新页面后恢复登录态")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.getCurrentUser());
    }

    @Operation(summary = "退出登录", description = "JWT 是无状态的，服务端不保存会话，前端删掉令牌即可；此接口留作记录操作日志")
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success("已退出登录", null);
    }
}
