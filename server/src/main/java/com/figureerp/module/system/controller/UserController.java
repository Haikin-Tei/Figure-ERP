package com.figureerp.module.system.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.system.dto.PasswordResetRequest;
import com.figureerp.module.system.dto.UserQuery;
import com.figureerp.module.system.dto.UserSaveRequest;
import com.figureerp.module.system.service.UserService;
import com.figureerp.module.system.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工（账号）管理接口。
 * <p>
 * 权限校验在 {@link UserService} 里做，Controller 只负责收参和封装响应。
 */
@Tag(name = "员工管理", description = "员工列表、新增、编辑、删除、重置密码、启停用")
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "分页查询员工")
    @GetMapping
    public Result<PageResult<UserVO>> page(@Valid UserQuery query) {
        return Result.success(userService.page(query));
    }

    @Operation(summary = "员工详情")
    @GetMapping("/{id}")
    public Result<UserVO> detail(@PathVariable Long id) {
        return Result.success(userService.detail(id));
    }

    @Operation(summary = "新增员工")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody UserSaveRequest request) {
        return Result.success("新增成功", userService.create(request));
    }

    @Operation(summary = "编辑员工", description = "登录账号不可修改；密码请用重置密码接口")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        userService.update(id, request);
        return Result.success("保存成功", null);
    }

    @Operation(summary = "删除员工", description = "逻辑删除，数据仍在库中；不能删除自己或最后一个管理员")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request.getNewPassword());
        return Result.success("密码已重置", null);
    }

    @Operation(summary = "启用 / 停用")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }
}
