package com.figureerp.common.exception;

import com.figureerp.common.result.ResultCode;

/**
 * 业务异常：Service 层遇到“可预期的失败”直接抛它，
 * 由 {@link GlobalExceptionHandler} 统一转成标准响应，不需要在 Controller 里 try-catch。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(ResultCode.BUSINESS_ERROR.getCode(), message);
    }

    public BusinessException(ResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMessage());
    }

    public BusinessException(ResultCode resultCode, String message) {
        this(resultCode.getCode(), message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
