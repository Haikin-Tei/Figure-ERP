package com.figureerp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密器。
 * <p>
 * 用 BCrypt 而不是 MD5/SHA：BCrypt 自带随机盐和可调计算强度，
 * 同样的明文每次哈希结果都不同，且无法被彩虹表反查。
 * <p>
 * 注意：BCrypt 每次校验都要算一遍，属于 CPU 密集操作，登录接口本身不会很「快」，
 * 这是刻意的设计（防暴力破解）。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
