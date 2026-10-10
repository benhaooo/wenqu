package com.wenqu.auth.config;

import com.wenqu.auth.api.CurrentUser;
import com.wenqu.auth.api.CurrentUserContext;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 从 Spring Security 上下文解析当前用户，供 controller 参数和其他模块使用。 */
@Component
public class CurrentUserSupport implements CurrentUserContext, WebMvcConfigurer {

    static {
        // CurrentUser 由服务端解析，不是接口参数
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Override
    public Optional<CurrentUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(new CurrentUser(Long.parseLong(jwt.getSubject())));
        }
        return Optional.empty();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == CurrentUser.class;
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory) {
                return current().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
            }
        });
    }
}
