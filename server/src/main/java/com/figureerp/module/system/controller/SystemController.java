package com.figureerp.module.system.controller;

import com.figureerp.common.constant.UserRole;
import com.figureerp.common.result.Result;
import com.figureerp.module.system.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统模块：健康检查 / 系统信息。
 * <p>
 * 这是脚手架阶段的示例 Controller，用来验证 Web + 统一响应 + 接口文档是否打通。
 * 后续业务模块（库存 stock、订单 order）按同样的目录结构放在 module 包下面。
 */
@Tag(name = "系统", description = "健康检查与系统信息")
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    @Value("${spring.application.name}")
    private String appName;

    @Value("${app.version:dev}")
    private String version;

    @Operation(summary = "健康检查", description = "用于确认后端服务是否已启动，不依赖数据库")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("app", appName);
        data.put("version", version);
        data.put("javaVersion", System.getProperty("java.version"));
        data.put("serverTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return Result.success(data);
    }

    @Operation(summary = "数据库连通检查", description = "真实取一次连接查询数据库版本与表数量，用于确认 MySQL 是否已打通")
    @GetMapping("/db")
    public Result<Map<String, Object>> db() {
        return Result.success(systemService.dbHealth());
    }

    @Operation(summary = "数据字典", description = "角色、账号状态，供前端下拉框使用（需登录）")
    @GetMapping("/dicts")
    public Result<Map<String, Object>> dicts() {
        Map<String, Object> data = new LinkedHashMap<>();
        // 角色按枚举顺序返回，前端按 group 分组渲染；level 用于前端判断按钮是否可点
        data.put("roles", Arrays.stream(UserRole.values())
                .map(role -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("code", role.getCode());
                    item.put("label", role.getLabel());
                    item.put("group", role.getGroup());
                    item.put("level", role.getLevel());
                    return item;
                })
                .toList());
        data.put("statuses", List.of(
                Map.of("code", 1, "label", "启用"),
                Map.of("code", 0, "label", "停用")
        ));
        return Result.success(data);
    }
}
