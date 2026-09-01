package com.xufg.common;

import lombok.Data;

/**
 * 统一响应结果。
 */
@Data
public class Result<T> {

    /** 业务状态码， 200 表示成功。 */
    private Integer code;

    /** 返回提示信息。 */
    private String message;

    /** 返回业务数据。 */
    private T data;

    /**
     * 构建成功响应。
     */
    public static <T> Result<T> ok(T data) {
        return build(200, "成功", data);
    }

    /**
     * 构建无数据的成功响应。
     */
    public static Result<Void> ok() {
        return ok(null);
    }

    /**
     * 构建失败响应。
     */
    public static <T> Result<T> error(Integer code, String message) {
        return build(code, message, null);
    }

    /**
     * 统一设置响应字段。
     */
    private static <T> Result<T> build(Integer code, String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
}
