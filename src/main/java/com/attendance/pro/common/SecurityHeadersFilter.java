package com.attendance.pro.common;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP 보안 헤더 필터(Spring Security 미도입 — 서블릿 필터로 구현).
 * 인터셉터가 아닌 필터인 이유: 에러 응답·정적 자원에도 헤더가 붙어야 하기 때문.
 * HSTS는 운영(TLS) 프로파일에서만 켠다({@code app.security.hsts=true}).
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "frame-ancestors 'none'",
            "base-uri 'self'",
            "form-action 'self'");

    private final boolean hstsEnabled;

    public SecurityHeadersFilter(@Value("${app.security.hsts:false}") boolean hstsEnabled) {
        this.hstsEnabled = hstsEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Permissions-Policy", "geolocation=(self), camera=(), microphone=(), payment=()");
        if (!req.getRequestURI().startsWith("/swagger-ui")) {  //개발용 Swagger UI만 CSP 제외(운영은 Swagger 자체 비활성)
            res.setHeader("Content-Security-Policy", CSP);
        }
        if (req.getRequestURI().startsWith("/api/")) {
            res.setHeader("Cache-Control", "no-store");        //출결/멤버/결제 조회 응답의 중간 캐시·디스크 캐시 방지
        }
        if (hstsEnabled) {
            res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        chain.doFilter(req, res);
    }

}
