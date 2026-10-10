package com.wenqu.workspace.controller;

import com.wenqu.auth.api.CurrentUserContext;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.workspace.api.RequireWorkspaceRole;
import com.wenqu.workspace.api.WorkspaceAccessApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 统一校验空间内接口的访问权限：路径含 {@code {workspaceId}} 的接口在参数解析和业务逻辑之前完成校验。
 *
 * <p>未标注 {@link RequireWorkspaceRole} 的空间内接口直接拒绝，避免遗漏鉴权。
 */
@Component
class WorkspaceAccessInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    static final String WORKSPACE_ID = "workspaceId";

    private final WorkspaceAccessApi workspaceAccess;
    private final CurrentUserContext currentUserContext;

    WorkspaceAccessInterceptor(WorkspaceAccessApi workspaceAccess, CurrentUserContext currentUserContext) {
        this.workspaceAccess = workspaceAccess;
        this.currentUserContext = currentUserContext;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        @SuppressWarnings("unchecked")
        var pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables == null || !pathVariables.containsKey(WORKSPACE_ID)) {
            return true;
        }
        RequireWorkspaceRole requirement = handlerMethod.getMethodAnnotation(RequireWorkspaceRole.class);
        if (requirement == null) {
            throw new IllegalStateException("空间内接口缺少 @RequireWorkspaceRole：" + handlerMethod);
        }
        long workspaceId = parseWorkspaceId(pathVariables.get(WORKSPACE_ID));
        long userId = currentUserContext
                .current()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED))
                .id();
        workspaceAccess.require(workspaceId, userId, requirement.value());
        return true;
    }

    private static long parseWorkspaceId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }
    }
}
