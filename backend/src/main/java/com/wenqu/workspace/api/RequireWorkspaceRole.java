package com.wenqu.workspace.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明访问空间内接口所需的最低角色。
 *
 * <p>路径中含有 {@code {workspaceId}} 的接口必须标注，由 workspace 模块在参数解析之前统一校验：不是成员返回 404，角色不足返回 403。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireWorkspaceRole {

    WorkspaceRole value();
}
