package com.attendance.pro.auth;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.user.Role;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 경로별 허용 role 화이트리스트 인가 인터셉터(AdminInterceptor 대체).
 * 인증은 {@code AuthInterceptor} 단일 책임, 인가는 이 인터셉터 단일 책임.
 * 규칙 테이블이 인가의 단일 소스다(서열 비교 없음).
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    private record RouteRule(String pattern, Set<Role> allowed) {
    }

    /** 선언 순서대로 첫 매칭 규칙 적용 */
    private static final List<RouteRule> RULES = List.of(
            new RouteRule("/api/v1/system/**", Set.of(Role.SYSTEM_ADMIN)),
            new RouteRule("/api/v1/admin/i18n/**", Set.of(Role.SYSTEM_ADMIN)),            //언어 마스터는 글로벌 제품 자산
            new RouteRule("/api/v1/tenant/**", Set.of(Role.TENANT_ADMIN)),                //SYSTEM_ADMIN도 403
            new RouteRule("/api/v1/attendance/**", Set.of(Role.TENANT_ADMIN, Role.MEMBER)) //SYSTEM_ADMIN 명시 배제
    );

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SessionUser user = AuthInterceptor.currentUser(request);
        if (user == null) {
            throw ApiException.unauthorized();      //401 error.unauthorized
        }
        for (RouteRule rule : RULES) {
            if (matcher.match(rule.pattern(), request.getRequestURI())) {
                if (!rule.allowed().contains(user.role())) {
                    throw ApiException.forbidden(); //403 error.forbidden
                }
                return true;
            }
        }
        return true;   //규칙에 없는 인증 필수 경로(auth/me 등)는 role 무관 통과
    }

}
