package com.attendance.pro.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.audit.AuditEvent;
import com.attendance.pro.audit.AuditService;
import com.attendance.pro.auth.AuthDtos.LoginRequest;
import com.attendance.pro.auth.AuthDtos.LoginResponse;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.config.LocaleConfig;
import com.attendance.pro.tenant.TenantHostResolver;
import com.attendance.pro.tenant.TenantHostResolver.HostTenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * 인증 API (세션 기반).
 */
@Tag(name = "Auth", description = "api.auth.tag")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;
    private final TenantHostResolver tenantHostResolver;
    private final AuditService auditService;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter,
            TenantHostResolver tenantHostResolver, AuditService auditService) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
        this.tenantHostResolver = tenantHostResolver;
        this.auditService = auditService;
    }

    @Operation(summary = "api.auth.login.summary", description = "api.auth.login.description")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "api.auth.login.200"),
            @ApiResponse(responseCode = "401", description = "api.auth.login.401"),
            @ApiResponse(responseCode = "429", description = "api.auth.login.429")
    })
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        //서브도메인/코드 병행: 호스트가 테넌트를 주장하면 호스트가 이긴다
        String tenantCode = resolveTenantCode(request, httpRequest);

        //①레이트 리밋 검사(임계 초과 시 429) → ②인증 → ③실패 기록/성공 초기화
        String clientIp = httpRequest.getRemoteAddr();
        loginRateLimiter.check(tenantCode, request.email(), clientIp);
        SessionUser user;
        try {
            user = authService.authenticate(tenantCode, request.email(), request.password());
        } catch (ApiException e) {
            boolean blockedNow = loginRateLimiter.recordFailure(tenantCode, request.email(), clientIp);
            //감사: 로그인 실패(존재 비노출 원칙상 user_id 없음 — 시도 이메일·테넌트·사유코드만)
            auditService.record(AuditEvent.LOGIN_FAIL, null, null, request.email(),
                    "tenant=" + tenantCode + " code=" + e.getCode(), httpRequest);
            //차단이 이번에 발동했으면(임계 도달) 1회만 별도 기록 — 차단 중 반복 시도는 check()에서
            //429로 걸러져 여기 도달하지 않으므로 감사 폭주 없이 무차별 시도 정황을 남긴다
            if (blockedNow) {
                auditService.record(AuditEvent.LOGIN_BLOCKED, null, null, request.email(),
                        "tenant=" + tenantCode + " rate limit engaged", httpRequest);
            }
            throw e;
        }
        loginRateLimiter.reset(tenantCode, request.email());

        //세션 고정 공격 방지를 위해 기존 세션을 무효화하고 새로 발급한다.
        //이때 언어 설정(LANG)은 새 세션으로 이어준다.
        HttpSession oldSession = httpRequest.getSession(false);
        Object lang = oldSession == null ? null : oldSession.getAttribute(LocaleConfig.SESSION_LANG_KEY);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = httpRequest.getSession(true);
        newSession.setAttribute(SessionUser.SESSION_KEY, user);
        if (lang != null) {
            newSession.setAttribute(LocaleConfig.SESSION_LANG_KEY, lang);
        }
        auditService.record(AuditEvent.LOGIN_SUCCESS, user.tenantId(), user.userId(), user.email(),
                null, httpRequest);
        return LoginResponse.from(user);
    }

    private String resolveTenantCode(LoginRequest request, HttpServletRequest httpRequest) {
        return resolveTenantCode(tenantHostResolver.resolve(httpRequest), request.tenantCode());
    }

    /**
     * 요청의 테넌트 코드를 확정한다(병행 규칙 — 로그인/비밀번호 재설정 요청 공용).
     *  - 테넌트 서브도메인 접속(FOUND/UNKNOWN): 호스트가 확정. 바디 코드가 있는데 다르면
     *    모호성을 조용히 삼키지 않고 400. UNKNOWN 코드는 후속 조회에서 통일 처리(존재 비노출).
     *  - 루트 도메인 접속(NONE): 기존 방식 — 바디 코드 필수.
     */
    static String resolveTenantCode(HostTenant hostTenant, String requestedCode) {
        String bodyCode = requestedCode == null ? "" : requestedCode.trim();
        if (hostTenant.claimsTenant()) {
            if (!bodyCode.isEmpty() && !bodyCode.equalsIgnoreCase(hostTenant.code())) {
                throw ApiException.badRequest("TENANT_CODE_MISMATCH", "auth.login.tenant-mismatch");
            }
            return hostTenant.code();
        }
        if (bodyCode.isEmpty()) {
            throw ApiException.badRequest("TENANT_CODE_REQUIRED", "validation.tenant-code.required");
        }
        return bodyCode;
    }

    @Operation(summary = "api.auth.logout.summary", description = "api.auth.logout.description")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            //감사: 로그아웃(세션 무효화 전 스냅샷에서 행위자 식별)
            Object su = session.getAttribute(SessionUser.SESSION_KEY);
            if (su instanceof SessionUser user) {
                auditService.record(AuditEvent.LOGOUT, user.tenantId(), user.userId(), user.email(),
                        null, httpRequest);
                //DB 세션 토큰을 비워 잔존 세션까지 정리(단일 세션 강제와 정합)
                authService.clearSession(user.tenantId(), user.userId());
            }
            //언어 설정은 로그아웃 후에도 유지한다
            Object lang = session.getAttribute(LocaleConfig.SESSION_LANG_KEY);
            session.invalidate();
            if (lang != null) {
                httpRequest.getSession(true).setAttribute(LocaleConfig.SESSION_LANG_KEY, lang);
            }
        }
    }

    @Operation(summary = "api.auth.me.summary", description = "api.auth.me.description")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "api.auth.me.200"),
            @ApiResponse(responseCode = "401", description = "api.auth.me.401")
    })
    @GetMapping("/me")
    public LoginResponse me(@LoginUser SessionUser user) {
        return LoginResponse.from(user);
    }

}
