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
    FILE_TOO_LARGE(10013, HttpStatus.CONTENT_TOO_LARGE, "文件超过大小限制"),
    UNSUPPORTED_MEDIA_TYPE(10015, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的请求格式"),
    INTERNAL_ERROR(10500, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),

    EMAIL_ALREADY_REGISTERED(11001, HttpStatus.CONFLICT, "该邮箱已注册"),
    INVALID_CREDENTIALS(11002, HttpStatus.UNAUTHORIZED, "邮箱或密码错误"),
    UNAUTHENTICATED(11003, HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    REFRESH_TOKEN_INVALID(11004, HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录"),

    WORKSPACE_PERMISSION_DENIED(12003, HttpStatus.FORBIDDEN, "当前角色没有此操作的权限"),
    WORKSPACE_NOT_FOUND(12004, HttpStatus.NOT_FOUND, "空间不存在或你不是该空间的成员"),
    INVITATION_NOT_FOUND(12010, HttpStatus.NOT_FOUND, "邀请链接无效"),
    INVITATION_EXPIRED(12011, HttpStatus.GONE, "邀请链接已过期"),
    INVITATION_REVOKED(12012, HttpStatus.GONE, "邀请链接已被撤销"),

    NODE_NOT_FOUND(13001, HttpStatus.NOT_FOUND, "文档或文件夹不存在"),
    INVALID_PARENT(13002, HttpStatus.BAD_REQUEST, "目标位置必须是当前空间中的文件夹"),
    MOVE_INTO_DESCENDANT(13003, HttpStatus.BAD_REQUEST, "不能移动到自身或其子文件夹中"),
    INVALID_SIBLING(13004, HttpStatus.BAD_REQUEST, "排序参照的节点不在目标文件夹中"),
    TRASH_ITEM_NOT_FOUND(13005, HttpStatus.NOT_FOUND, "回收站中没有该项"),
    DOCUMENT_VERSION_CONFLICT(13006, HttpStatus.CONFLICT, "文档已被其他人修改，请刷新后再编辑"),
    INVALID_CONTENT(13007, HttpStatus.BAD_REQUEST, "文档内容格式不正确"),
    CONTENT_TOO_LARGE(13008, HttpStatus.CONTENT_TOO_LARGE, "文档内容过大"),
    UNSUPPORTED_IMAGE_TYPE(13009, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "只支持 PNG、JPEG、GIF、WebP 图片"),
    IMAGE_NOT_FOUND(13011, HttpStatus.NOT_FOUND, "图片不存在"),

    UNSUPPORTED_IMPORT_TYPE(14001, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "只支持 PDF、Word、Markdown 和纯文本文件"),
    INGEST_JOB_NOT_FOUND(14002, HttpStatus.NOT_FOUND, "导入任务不存在"),
    INGEST_JOB_NOT_RETRYABLE(14003, HttpStatus.CONFLICT, "只有失败的任务可以重试"),
    SOURCE_FILE_NOT_FOUND(14004, HttpStatus.NOT_FOUND, "该文档不是导入的文件"),

    CONVERSATION_NOT_FOUND(16001, HttpStatus.NOT_FOUND, "会话不存在"),
    MESSAGE_NOT_FOUND(16002, HttpStatus.NOT_FOUND, "回答不存在"),
    INVALID_SCOPE(16003, HttpStatus.BAD_REQUEST, "提问范围中的文件夹或文档不存在"),
    AI_UNAVAILABLE(16004, HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂时不可用，请稍后再试"),
    NOTHING_TO_REGENERATE(16005, HttpStatus.CONFLICT, "会话中还没有问题");

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
