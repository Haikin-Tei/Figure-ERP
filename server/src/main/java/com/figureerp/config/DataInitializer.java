package com.figureerp.config;

import com.figureerp.common.constant.UserRole;
import com.figureerp.module.system.entity.SysUser;
import com.figureerp.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时初始化一个管理员账号，避免「表建好了但没人能登录」。
 * <p>
 * 只在用户表为空时插入，已有数据不会覆盖。开关与初始密码都在 application.yml 的 app.init-admin 下，
 * 上线前建议把 enabled 改成 false，或用 ADMIN_INIT_PASSWORD 环境变量指定强密码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.init-admin.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init-admin.username:admin}")
    private String username;

    @Value("${app.init-admin.password:admin123}")
    private String password;

    @Value("${app.init-admin.real-name:系统管理员}")
    private String realName;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long count = userMapper.selectCount(null);
            if (count != null && count > 0) {
                return;
            }

            SysUser admin = new SysUser();
            admin.setUsername(username);
            admin.setRealName(realName);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole(UserRole.ADMIN.getCode());
            admin.setIntro("系统内置管理员账号，首次启动自动创建");
            admin.setRemark("上线前请登录后修改密码");
            admin.setStatus(1);
            userMapper.insert(admin);

            log.warn("已创建初始管理员账号：{} / {}，请尽快登录并修改密码", username, password);
        } catch (Exception e) {
            // 表还没建时不要让整个应用起不来，只提示怎么处理
            log.error("初始化管理员账号失败，请确认已执行 db/schema.sql 建表: {}", e.getMessage());
        }
    }
}
