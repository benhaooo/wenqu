package com.wenqu.auth.config;

import com.wenqu.common.error.ErrorCode;
import com.wenqu.common.web.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * 无状态鉴权：除公开接口外，请求必须携带有效的 Access Token（Bearer JWT）。
 *
 * <p>不启用 CSRF 防护：业务接口靠 Authorization 头鉴权，浏览器不会跨站自动携带；只有刷新与退出接口使用 Cookie，该 Cookie 为
 * SameSite=Strict，跨站请求不会携带。
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/system/health")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> {})
                        .authenticationEntryPoint(
                                (request, response, e) -> write(response, jsonMapper, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler(
                                (request, response, e) -> write(response, jsonMapper, ErrorCode.FORBIDDEN)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                (request, response, e) -> write(response, jsonMapper, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler(
                                (request, response, e) -> write(response, jsonMapper, ErrorCode.FORBIDDEN)))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void write(HttpServletResponse response, JsonMapper jsonMapper, ErrorCode errorCode)
            throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getWriter(), ApiResult.error(errorCode, errorCode.defaultMessage()));
    }
}
