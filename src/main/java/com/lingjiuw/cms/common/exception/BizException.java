package com.lingjiuw.cms.common.exception;

import lombok.Getter;

/**
 * 业务异常：抛出后由 {@link GlobalExceptionHandler} 转为统一响应包，HTTP 状态仍为 200。
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(String message) {
        this(1, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 带上根因抛出：调用点不必再把 cause 拼进消息里，原始堆栈与异常类型不会丢 */
    public BizException(String message, Throwable cause) {
        this(1, message, cause);
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
