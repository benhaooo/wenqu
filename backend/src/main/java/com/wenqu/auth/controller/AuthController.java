package com.wenqu.auth.controller;

import com.wenqu.auth.api.CurrentUser;
import com.wenqu.auth.config.AuthProperties;
import com.wenqu.auth.controller.AuthRequests.LoginRequest;
import com.wenqu.auth.controller.AuthRequests.RegisterRequest;
import com.wenqu.auth.domain.IssuedTokens;
import com.wenqu.auth.service.AuthService;
import com.wenqu.auth.service.UserQueryService;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.common.web.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "auth", description = "账号与登录")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "wenqu_refresh";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final UserQueryService userQueryService;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthController(
            AuthService authService, UserQueryService userQueryService, AuthProperties properties, Clock clock) {
        this.authService = authService;
        this.userQueryService = userQueryService;
        this.properties = properties;
        this.clock = clock;
    }

    @Operation(operationId = "register", summary = "注册并登录")
    @PostMapping("/register")
    public ResponseEntity<ApiResult<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return withTokens(authService.register(request.email(), request.password(), request.displayName()));
    }

    @Operation(operationId = "login", summary = "登录")
    @PostMapping("/login")
    public ResponseEntity<ApiResult<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return withTokens(authService.login(request.email(), request.password()));
    }

    @Operation(operationId = "refresh", summary = "用 Cookie 中的 Refresh Token 换取新的令牌")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResult<TokenResponse>> refresh(
            @Parameter(hidden = true) @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return withTokens(authService.refresh(refreshToken));
    }

    @Operation(operationId = "logout", summary = "退出登录")
    @PostMapping("/logout")
    public ResponseEntity<ApiResult<Void>> logout(
            @Parameter(hidden = true) @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .body(ApiResult.ok(null));
    }

    @Operation(operationId = "getCurrentUser", summary = "当前登录用户")
    @GetMapping("/me")
    public ApiResult<UserResponse> me(CurrentUser currentUser) {
        return ApiResult.ok(UserResponse.from(userQueryService.getById(currentUser.id())));
    }

    private ResponseEntity<ApiResult<TokenResponse>> withTokens(IssuedTokens tokens) {
        Duration maxAge = Duration.between(clock.instant(), tokens.refreshTokenExpiresAt());
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie(tokens.refreshToken(), maxAge).toString())
                .body(ApiResult.ok(TokenResponse.from(tokens)));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
