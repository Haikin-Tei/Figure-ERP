package com.figureerp.module.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增 / 编辑员工。
 * <p>
 * 编辑时 username 不可改（它是登录身份，改了等于换人），password 也不在这里改，
 * 走单独的「重置密码」接口。
 */
@Data
@Schema(description = "员工保存参数")
public class UserSaveRequest {

    @Schema(description = "登录账号，仅新增时生效", example = "zhangsan")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "账号只能由字母、数字、下划线组成，长度 3-20 位")
    private String username;

    @Schema(description = "员工姓名", example = "张三")
    @NotBlank(message = "请输入员工姓名")
    @Size(max = 50, message = "姓名不能超过 50 个字符")
    private String realName;

    @Schema(description = "登录密码，仅新增时生效，至少 6 位")
    @Size(max = 64, message = "密码不能超过 64 个字符")
    private String password;

    @Schema(description = "角色编码，**必填**：ADMIN 管理员 / GM 总经理 / FINANCE_MANAGER 财务主管 / "
            + "FINANCE_STAFF 财务专员 / SALES_MANAGER 销售主管 / SALES_STAFF 销售专员 / "
            + "LOGISTICS_MANAGER 物流主管 / LOGISTICS_STAFF 物流专员 / HR_MANAGER 人事主管 / HR_STAFF 人事专员；"
            + "可选值以 GET /api/system/dicts 为准", example = "FINANCE_STAFF",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择角色")
    @Size(max = 20, message = "角色编码不能超过 20 个字符")
    private String role;

    @Schema(description = "账号状态：1 启用，0 停用")
    @NotNull(message = "请选择账号状态")
    @Min(value = 0, message = "状态值只能是 0 或 1")
    @Max(value = 1, message = "状态值只能是 0 或 1")
    private Integer status;

    @Schema(description = "员工介绍")
    @Size(max = 500, message = "员工介绍不能超过 500 个字符")
    private String intro;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;
}
