package com.figureerp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 相关配置，对应 application.yml 里的 app.jwt.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /**
     * 签名密钥。HS256 要求至少 256 bit（32 个字节），太短会直接抛 WeakKeyException。
     * 生产环境必须用环境变量 JWT_SECRET 覆盖，不要用仓库里的默认值。
     */
    private String secret = "figure-erp-default-jwt-secret-please-change-in-prod-2026";

    /** 令牌有效期（秒），默认 8 小时，正好一个工作日 */
    private long expireSeconds = 28800;

    /** 签发方标识 */
    private String issuer = "figure-erp";
}
