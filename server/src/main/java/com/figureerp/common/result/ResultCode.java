package com.figureerp.common.result;

/**
 * 统一业务状态码。
 * <p>
 * 约定：200 表示成功，4xx 表示客户端问题，5xx 表示服务端问题，
 * 1xxx 段留给具体业务错误（各模块可继续往下扩展）。
 */
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限访问"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    FAIL(500, "操作失败"),
    SERVER_ERROR(500, "系统内部错误"),

    BUSINESS_ERROR(1000, "业务处理失败"),
    DATA_NOT_FOUND(1001, "数据不存在"),
    DATA_ALREADY_EXISTS(1002, "数据已存在"),
    PARAM_INVALID(1003, "参数校验失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
