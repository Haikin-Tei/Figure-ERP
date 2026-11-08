package com.figureerp.config;

import com.figureerp.common.context.LoginUser;
import com.figureerp.common.context.UserContext;
import com.figureerp.common.result.Result;
import com.figureerp.common.result.ResultCode;
import com.figureerp.common.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器：校验 Authorization: Bearer {token}，通过后把用户放进 UserContext。
 * <p>
 * 这里故意用拦截器而不是整套 Spring Security：项目目前只需要「登录 + 角色权限控制」，
 * 上 Security 反而要写一大堆过滤器配置。等权限模型复杂了再换也不迟。
 * <p>
 * 响应约定：未登录同样返回 HTTP 200 + code 401，与全局异常处理保持一致，
 * 前端只需看 body 里的 code。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 跨域预检请求不带令牌，直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            writeUnauthorized(response, "请先登录");
            return false;
        }

        try {
            LoginUser loginUser = jwtUtil.parse(token);
            UserContext.set(loginUser);
            return true;
        } catch (ExpiredJwtException e) {
            writeUnauthorized(response, "登录已过期，请重新登录");
            return false;
        } catch (Exception e) {
            log.warn("令牌解析失败: {}", e.getMessage());
            writeUnauthorized(response, "登录凭证无效，请重新登录");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 必须清理，否则 Tomcat 线程复用时会把上一个请求的用户带过来
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> body = Result.fail(ResultCode.UNAUTHORIZED, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
