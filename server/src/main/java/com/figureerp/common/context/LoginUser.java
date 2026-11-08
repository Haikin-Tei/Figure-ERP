package com.figureerp.common.context;

/**
 * 当前请求的登录用户信息，由 JWT 解析而来。
 * <p>
 * 只放鉴权与展示必需的字段，不放密码等敏感信息。
 */
public record LoginUser(Long id, String username, String realName) {
}
