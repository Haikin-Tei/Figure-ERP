package com.figureerp.module.system.converter;

import com.figureerp.common.constant.UserRole;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.vo.UserVO;

/**
 * 用户实体 → 出参的转换。
 * <p>
 * 单独抽出来是因为 AuthService 和 UserService 都要用；
 * 放在这里可以保证「哪些字段能返回给前端」只有一处定义，不会漏掉 password 过滤。
 */
public final class UserConverter {

    private UserConverter() {
    }

    public static UserVO toVO(SysUser user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setRoleLabel(UserRole.labelOf(user.getRole()));
        // 前端用它判断按钮是否可点，避免各页面再写一份角色清单
        UserRole role = UserRole.of(user.getRole());
        vo.setRoleGroup(role == null ? null : role.getGroup());
        vo.setRoleLevel(UserRole.levelOf(user.getRole()));
        vo.setStatus(user.getStatus());
        vo.setIntro(user.getIntro());
        vo.setRemark(user.getRemark());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
