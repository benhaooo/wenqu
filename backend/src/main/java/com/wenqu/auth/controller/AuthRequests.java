package com.wenqu.auth.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AuthRequests {

    private AuthRequests() {}

    record RegisterRequest(
            @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Size(max = 254)
            String email,

            @NotBlank(message = "密码不能为空") @Size(min = 8, max = 72, message = "密码长度为 8～72 位")
            String password,

            @NotBlank(message = "昵称不能为空") @Size(max = 30, message = "昵称最多 30 个字符")
            String displayName) {}

    record LoginRequest(
            @NotBlank(message = "邮箱不能为空") String email,
            @NotBlank(message = "密码不能为空") String password) {}
}
