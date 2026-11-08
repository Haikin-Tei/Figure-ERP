package com.figureerp.module.system.service;

import com.figureerp.common.constant.UserRole;
import com.figureerp.common.context.UserContext;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.ResultCode;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 当前登录用户的查询与权限判断，供各业务模块复用。
 * <p>
 * 关键点：每次都从数据库读，而不是用令牌里的角色快照。
 * 这样管理员刚把某人降级，下一个请求就会立刻失效，不用等令牌过期。
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final SysUserMapper userMapper;

    /** 当前登录用户，账号已被删除时抛 401 */
    public SysUser require() {
        Long id = UserContext.requireId();
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前账号不存在或已被删除");
        }
        return user;
    }

    public String role() {
        return require().getRole();
    }

    /** 管理员或总经理 → 可以维护员工账号 */
    public boolean isAdmin() {
        return UserRole.isAdmin(role());
    }

    /** 主管级及以上 → 可以维护业务配置（物流价格、仓库设置、商品等） */
    public boolean canManage() {
        return UserRole.canManage(role());
    }

    /** 需要主管级及以上，业务模块直接用这个挡住越权操作 */
    public SysUser requireManager(String action) {
        SysUser user = require();
        if (!UserRole.canManage(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "没有权限" + action + "，请联系管理员或主管");
        }
        return user;
    }

    /** 需要管理级（管理员 / 总经理） */
    public SysUser requireAdmin(String action) {
        SysUser user = require();
        if (!UserRole.isAdmin(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有管理员或总经理可以" + action);
        }
        return user;
    }
}
