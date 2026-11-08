package com.figureerp.common.context;

import com.figureerp.common.exception.BusinessException;
import com.figureerp.common.result.ResultCode;

/**
 * 当前登录用户的线程上下文。
 * <p>
 * 由 {@code AuthInterceptor} 在请求进入时写入、请求结束时清理。
 * 业务代码里想拿当前用户，直接 {@code UserContext.get()} / {@code UserContext.requireId()}，
 * 不用在每层方法上都传一遍 userId 参数。
 * <p>
 * 注意：ThreadLocal 必须在请求结束时 clear，否则线程池复用会串号。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 可能为 null（比如白名单接口） */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 需要登录才能执行的操作，用它取当前用户，取不到直接 401 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    public static Long requireId() {
        return require().id();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
