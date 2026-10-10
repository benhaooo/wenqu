package com.wenqu.common.error;

import org.springframework.http.HttpStatus;

/** 全部错误码。号段划分见 docs/conventions/api.md。 */
public enum ErrorCode {
    SUCCESS(0, HttpStatus.OK, "成功"),

    BAD_REQUEST(10000, HttpStatus.BAD_REQUEST, "请求格式错误"),
    VALIDATION_FAILED(10001, HttpStatus.BAD_REQUEST, "参数校验失败"),
    FORBIDDEN(10003, HttpStatus.FORBIDDEN, "没有权限"),
    NOT_FOUND(10004, HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(10005, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    INTERNAL_ERROR(10500, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),

    EMAIL_ALREADY_REGISTERED(11001, HttpStatus.CONFLICT, "该邮箱已注册"),
    INVALID_CREDENTIALS(11002, HttpStatus.UNAUTHORIZED, "邮箱或密码错误"),
    UNAUTHENTICATED(11003, HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    REFRESH_TOKEN_INVALID(11004, HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录"),

    WORKSPACE_PERMISSION_DENIED(12003, HttpStatus.FORBIDDEN, "当前角色没有此操作的权限"),
    WORKSPACE_NOT_FOUND(12004, HttpStatus.NOT_FOUND, "空间不存在或你不是该空间的成员"),
    INVITATION_NOT_FOUND(12010, HttpStatus.NOT_FOUND, "邀请链接无效"),
    INVITATION_EXPIRED(12011, HttpStatus.GONE, "邀请链接已过期"),
    INVITATION_REVOKED(12012, HttpStatus.GONE, "邀请链接已被撤销");

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
