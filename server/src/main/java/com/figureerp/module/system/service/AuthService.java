package com.figureerp.module.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.common.context.UserContext;
import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.ResultCode;
import com.figureerp.common.util.JwtUtil;
import com.figureerp.module.system.converter.UserConverter;
import com.figureerp.module.system.dto.LoginRequest;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import com.figureerp.module.system.vo.LoginVO;
import com.figureerp.module.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证服务：登录、取当前用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int STATUS_ENABLED = 1;

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 账号密码登录。
     * <p>
     * 「账号不存在」和「密码错误」返回完全相同的提示，避免被用来逐个试探账号是否存在。
     */
    public LoginVO login(LoginRequest request) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername()));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("登录失败，账号或密码不正确: {}", request.getUsername());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码不正确");
        }

        if (!Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该账号已被停用，请联系管理员");
        }

        // 记录最后登录时间：只更新这一列，其余字段不动
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(update);

        String token = jwtUtil.generate(user);
        log.info("用户登录成功: {} ({})", user.getUsername(), user.getRealName());

        return LoginVO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpireSeconds())
                .user(UserConverter.toVO(user))
                .build();
    }

    /** 取当前登录用户（每次从库里读，保证角色/状态变更后立即生效） */
    public UserVO getCurrentUser() {
        Long userId = UserContext.requireId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号不存在或已被删除");
        }
        return UserConverter.toVO(user);
    }
}
