package com.wenqu.auth.controller;

import com.wenqu.auth.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName) {

    static UserResponse from(User user) {
        return new UserResponse(user.id(), user.email(), user.displayName());
    }
}
