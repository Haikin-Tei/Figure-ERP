package com.figureerp.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户（员工）。
 * <p>
 * 一个账号对应一名员工，登录用 username + password，
 * 其余字段（姓名、介绍、备注）用于展示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 登录账号 */
    private String username;

    /** 员工姓名 */
    private String realName;

    /** BCrypt 加密后的密码，任何接口都不得返回 */
    private String password;

    /** 角色（使用权限）：ADMIN 管理员 / MANAGER 主管 / STAFF 员工，见 UserRole */
    private String role;

    /** 头像地址 */
    private String avatar;

    /** 员工介绍 */
    private String intro;

    /** 备注 */
    private String remark;

    /** 状态：1 启用，0 禁用（禁用后不能登录，但数据保留） */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;
}
