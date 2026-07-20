package com.attendance.pro.auth;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.attendance.pro.common.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CSRF 심층방어 — 상태변경(POST/PUT/DELETE/PATCH) 요청에 커스텀 헤더(X-Requested-With)를 요구한다.
 * 비단순(non-simple) 헤더라 교차출처(cross-site) HTML form/이미지 요청으로는 만들 수 없고,
 * fetch/XHR로 붙이려면 CORS 프리플라이트가 필요한데 CORS는 기본 차단이라 통과하지 못한다.
 * (기존 SameSite=Lax + JSON 바디 요구와 합쳐 방어를 이중화한다.)
 * SPA는 client.ts가 모든 요청에 이 헤더를 자동으로 붙인다.
 */
@Component
public class CsrfHeaderInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final String REQUIRED_HEADER = "X-Requested-With";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (MUTATING.contains(request.getMethod())) {
            String header = request.getHeader(REQUIRED_HEADER);
            if (header == null || header.isBlank()) {
                throw ApiException.forbidden(); //403 error.forbidden — CSRF 방어
            }
        }
        return true;
    }
}
