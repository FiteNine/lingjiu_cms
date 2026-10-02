package com.lingjiuw.cms.common.api;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应包。code = 0 表示成功，非 0 为业务错误，message 为中文提示。
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 默认非成功值：只有 ok() 才显式置 0，避免漏设 code 的实例被误判成成功响应 */
    private int code = -1;
    private String message;
    private T data;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static <T> Result<T> error(String message) {
        return error(1, message);
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
