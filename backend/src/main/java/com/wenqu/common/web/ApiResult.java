package com.wenqu.common.web;

import com.wenqu.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

/** 统一响应格式，见 docs/conventions/api.md。 */
public record ApiResult<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "0 表示成功，其他为错误码")
        int code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "业务数据，出错时为 null")
        T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.defaultMessage(), data);
    }

    public static ApiResult<Void> error(ErrorCode errorCode, String message) {
        return new ApiResult<>(errorCode.code(), message, null);
    }
}
