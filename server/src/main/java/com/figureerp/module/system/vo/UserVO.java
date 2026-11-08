package com.figureerp.module.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息出参。
 * <p>
 * 和实体分开的原因：实体里有 password，直接返回会把密码哈希泄给前端。
 */
@Data
@Schema(description = "用户信息")
public class UserVO {

    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "员工姓名")
    private String realName;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "角色编码：ADMIN / GM / FINANCE_MANAGER / FINANCE_STAFF / …，见 UserRole")
    private String role;

    @Schema(description = "角色文案，如「财务主管」")
    private String roleLabel;

    @Schema(description = "角色板块，前端下拉分组用：系统 / 管理层 / 财务 / 销售 / 物流 / 人事 / 通用")
    private String roleGroup;

    @Schema(description = "权限层级：3 管理级（可维护员工） / 2 主管级（可写业务） / 1 执行级（只读）")
    private Integer roleLevel;

    @Schema(description = "账号状态：1 启用，0 停用")
    private Integer status;

    @Schema(description = "员工介绍")
    private String intro;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
