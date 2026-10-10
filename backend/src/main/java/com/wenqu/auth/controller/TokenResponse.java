package com.wenqu.auth.controller;

import com.wenqu.auth.domain.IssuedTokens;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Access Token 在响应体中返回；Refresh Token 只通过 HttpOnly Cookie 下发。 */
public record TokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant accessTokenExpiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UserResponse user) {

    static TokenResponse from(IssuedTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.accessTokenExpiresAt(), UserResponse.from(tokens.user()));
    }
}
