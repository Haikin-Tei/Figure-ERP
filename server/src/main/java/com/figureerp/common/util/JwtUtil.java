package com.figureerp.common.util;

import com.figureerp.common.context.LoginUser;
import com.figureerp.config.JwtProperties;
import com.figureerp.module.system.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与解析。
 * <p>
 * 令牌里只放 id / username / realName 这类非敏感信息，
 * 密码等敏感信息一律不进令牌（JWT 是 Base64 编码，不是加密，任何人解出来都能看）。
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_REAL_NAME = "realName";

    private final JwtProperties properties;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 登录成功后签发令牌 */
    public String generate(SysUser user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + properties.getExpireSeconds() * 1000);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_REAL_NAME, user.getRealName())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key())
                .compact();
    }

    /**
     * 解析令牌。签名不对、格式错、已过期都会抛 JwtException，由调用方决定怎么响应。
     */
    public LoginUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new LoginUser(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                claims.get(CLAIM_REAL_NAME, String.class)
        );
    }

    public long getExpireSeconds() {
        return properties.getExpireSeconds();
    }
}
