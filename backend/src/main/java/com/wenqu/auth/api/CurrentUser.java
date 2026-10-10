package com.wenqu.auth.api;

/** 当前登录用户。controller 方法声明此类型的参数即可获得，无需依赖 Spring Security。 */
public record CurrentUser(long id) {}
