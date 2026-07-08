package com.attendance.pro.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.common.Messages;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 관리자 전용 API(/api/v1/admin/**)의 권한 검사 인터셉터.
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final Messages messages;

    public AdminInterceptor(Messages messages) {
        this.messages = messages;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SessionUser user = AuthInterceptor.currentUser(request);
        if (user == null) {
            throw ApiException.unauthorized(messages.get("error.unauthorized"));
        }
        if (!user.admin()) {
            throw ApiException.forbidden(messages.get("error.forbidden"));
        }
        return true;
    }

}
