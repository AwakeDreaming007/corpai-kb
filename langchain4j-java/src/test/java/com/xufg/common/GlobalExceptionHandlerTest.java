package com.xufg.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 全局异常处理器单元测试。
 */
class GlobalExceptionHandlerTest {

    /**
     * 上传超过限制时必须返回明确的 400，不能落入系统繁忙。
     */
    @Test
    void shouldMapMaxUploadSizeToBadRequest() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        Result<Void> result = handler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(100L));

        assertEquals(400, result.getCode());
        assertEquals("文件超过大小限制", result.getMessage());
    }
}
