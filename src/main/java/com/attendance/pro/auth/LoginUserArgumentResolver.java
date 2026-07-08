package com.attendance.pro.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.common.Messages;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@link LoginUser} 어노테이션이 붙은 파라미터에 세션 유저를 주입한다.
 */
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final Messages messages;

    public LoginUserArgumentResolver(Messages messages) {
        this.messages = messages;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && parameter.getParameterType().equals(SessionUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        SessionUser user = AuthInterceptor.currentUser(request);
        if (user == null) {
            throw ApiException.unauthorized(messages.get("error.unauthorized"));
        }
        return user;
    }

}
