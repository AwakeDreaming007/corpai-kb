package com.xufg.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException exception) {
        return Result.error(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理请求体校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
        return Result.error(400, message);
    }

    /**
     * 处理表单绑定校验异常。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
        return Result.error(400, message);
    }

    /**
     * 处理路径变量或请求参数类型不匹配。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        return Result.error(400, "参数类型错误");
    }

    /**
     * 处理必填请求参数缺失，属于客户端错误而非服务端异常。
     */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public Result<Void> handleMissingServletRequestParameterException(
            org.springframework.web.bind.MissingServletRequestParameterException exception) {
        return Result.error(400, "缺少必填参数: " + exception.getParameterName());
    }

    /**
     * 处理上传文件超过大小限制，属于客户端错误而非服务端异常。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception) {
        return Result.error(400, "文件超过大小限制");
    }

    /**
     * 单独处理权限不足， 防止落入兜底异常。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDeniedException(AccessDeniedException exception) {
        return Result.error(403, "权限不足");
    }

    /**
     * 处理认证异常。
     */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthenticationException(AuthenticationException exception) {
        return Result.error(401, "未登录或登录已过期");
    }

    /**
     * 处理请求体解析失败（非法 JSON、非法编码等），属于客户端错误。
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(
            org.springframework.http.converter.HttpMessageNotReadableException exception) {
        return Result.error(400, "请求体格式错误");
    }

    /**
     * 处理请求方法不支持（如路径存在但方法不匹配），避免落入 500 兜底。
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleHttpRequestMethodNotSupportedException(
            org.springframework.web.HttpRequestMethodNotSupportedException exception) {
        return Result.error(405, "请求方法不支持");
    }

    /**
     * 处理未知异常， 避免暴露服务端细节。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        log.error("系统发生未处理异常", exception);
        return Result.error(500, "系统繁忙");
    }
}
