package com.wenqu.common.error;

import org.springframework.http.HttpStatus;

/** 全部错误码。号段划分见 docs/conventions/api.md。 */
public enum ErrorCode {
    SUCCESS(0, HttpStatus.OK, "成功"),

    BAD_REQUEST(10000, HttpStatus.BAD_REQUEST, "请求格式错误"),
    VALIDATION_FAILED(10001, HttpStatus.BAD_REQUEST, "参数校验失败"),
    NOT_FOUND(10004, HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(10005, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    INTERNAL_ERROR(10500, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
