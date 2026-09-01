package com.xufg.common;

import lombok.Getter;

/**
 * 业务异常，用于向前端返回明确的错误提示。
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务错误码。 */
    private final Integer code;

    /**
     * 使用默认错误码 400 创建业务异常。
     */
    public BizException(String message) {
        this(400, message);
    }

    /**
     * 使用指定错误码创建业务异常。
     */
    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
