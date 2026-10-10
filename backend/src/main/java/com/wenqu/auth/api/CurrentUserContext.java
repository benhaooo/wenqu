package com.wenqu.auth.api;

import java.util.Optional;

/** 在非 controller 参数的场景（如拦截器）获取当前登录用户。 */
public interface CurrentUserContext {

    Optional<CurrentUser> current();
}
