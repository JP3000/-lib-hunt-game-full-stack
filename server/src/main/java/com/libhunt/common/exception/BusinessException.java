package com.libhunt.common.exception;

/**
 * 业务异常：可预期的错误（参数不对、无权访问、状态冲突等）。
 * 由 {@link GlobalExceptionHandler} 统一转换为 HTTP 200 + 业务错误码，
 * 不打印堆栈，避免日志被业务噪音淹没。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
