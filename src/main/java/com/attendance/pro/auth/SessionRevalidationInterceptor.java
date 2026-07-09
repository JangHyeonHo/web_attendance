package com.attendance.pro.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.attendance.pro.config.LocaleConfig;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantHostResolver;
import com.attendance.pro.tenant.TenantHostResolver.HostTenant;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.tenant.TenantStatus;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;
import com.attendance.pro.user.UserStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 세션 스냅샷과 DB 현재 상태의 요청 단위 재검증.
 *
 * SessionUser는 로그인 시점의 스냅샷이라, 이후의 테넌트 정지·계정 비활성·role 변경이
 * 기존 세션에 반영되지 않으면 세션 타임아웃(20분)까지 권한이 잔존한다.
 * 이 인터셉터가 매 요청마다 유저/테넌트 상태를 재확인해 그 간극을 없앤다:
 *  - 유저 삭제/비활성 또는 테넌트 SUSPENDED → 세션 즉시 무효화(언어 설정만 이월). 이후
 *    AuthInterceptor가 401로 응답하고, 공개 API(navigation 등)는 비로그인으로 취급된다.
 *  - role이 바뀐 경우 → 세션 스냅샷을 현재 role로 교체(RoleInterceptor가 최신 role로 인가).
 *  - 테넌트 서브도메인 요청인데 세션의 테넌트와 다르면 → 세션 무효화
 *    (쿠키는 host-only가 기본이지만, 쿠키 수동 이식까지 서버측에서 차단하는 이중 방어).
 *
 * 등록은 WebConfig에서 /api/** 전체(공개 경로 포함)에, Auth/Role 인터셉터보다 앞순서로 한다.
 * 세션이 없는 요청은 통과(인증 요구는 AuthInterceptor의 책임).
 */
@Component
public class SessionRevalidationInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final TenantHostResolver tenantHostResolver;

    public SessionRevalidationInterceptor(UserMapper userMapper, TenantMapper tenantMapper,
            TenantHostResolver tenantHostResolver) {
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.tenantHostResolver = tenantHostResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        SessionUser sessionUser =
                session == null ? null : (SessionUser) session.getAttribute(SessionUser.SESSION_KEY);
        if (sessionUser == null) {
            return true;
        }

        //세션-호스트 일치: 테넌트 서브도메인에서는 그 테넌트의 세션만 유효하다
        HostTenant hostTenant = tenantHostResolver.resolve(request);
        if (hostTenant.claimsTenant()
                && (hostTenant.tenant() == null
                        || hostTenant.tenant().tenantId() != sessionUser.tenantId())) {
            invalidateKeepingLang(request, session);
            return true;
        }

        User current = userMapper.findById(sessionUser.tenantId(), sessionUser.userId());
        Tenant tenant = tenantMapper.findById(sessionUser.tenantId());
        boolean userRevoked = current == null || current.status() != UserStatus.ACTIVE;
        boolean tenantRevoked = tenant == null || tenant.status() == TenantStatus.SUSPENDED;
        if (userRevoked || tenantRevoked) {
            invalidateKeepingLang(request, session);
            return true;
        }
        if (current.role() != sessionUser.role()) {
            //강등/승격 즉시 반영 — RoleInterceptor가 이 갱신된 스냅샷으로 인가한다
            session.setAttribute(SessionUser.SESSION_KEY,
                    new SessionUser(sessionUser.userId(), sessionUser.tenantId(),
                            sessionUser.tenantCode(), sessionUser.tenantName(),
                            sessionUser.email(), sessionUser.name(), current.role()));
        }
        return true;
    }

    /** 로그아웃과 동일하게 언어 설정만 새 세션으로 이월하고 무효화한다. */
    private void invalidateKeepingLang(HttpServletRequest request, HttpSession session) {
        Object lang = session.getAttribute(LocaleConfig.SESSION_LANG_KEY);
        session.invalidate();
        if (lang != null) {
            request.getSession(true).setAttribute(LocaleConfig.SESSION_LANG_KEY, lang);
        }
    }

}
