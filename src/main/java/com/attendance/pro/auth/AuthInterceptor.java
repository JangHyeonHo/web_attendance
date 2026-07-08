package com.attendance.pro.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.attendance.pro.common.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 로그인 필수 API의 세션 검사 인터셉터.
 * 적용/제외 경로는 {@code WebConfig}에서 등록한다.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (currentUser(request) == null) {
            throw ApiException.unauthorized();
        }
        return true;
    }

    static SessionUser currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
    }

}
