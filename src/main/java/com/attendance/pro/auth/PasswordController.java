package com.attendance.pro.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.AuthDtos.PasswordResetRequest;
import com.attendance.pro.auth.AuthDtos.PasswordSetRequest;
import com.attendance.pro.auth.AuthDtos.TokenVerifyRequest;
import com.attendance.pro.auth.AuthDtos.TokenVerifyResponse;
import com.attendance.pro.tenant.TenantHostResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 공개 비밀번호 설정/재설정 API (WebConfig authInterceptor exclude — 공개 4번째 경로).
 * 토큰은 URL이 아닌 <b>바디로만</b> 받는다(액세스 로그·Referer 유출 방지).
 */
@Tag(name = "Password", description = "api.password.tag")
@RestController
@RequestMapping("/api/v1/auth/password")
public class PasswordController {

    private final PasswordService passwordService;
    private final PasswordResetRateLimiter rateLimiter;
    private final TenantHostResolver tenantHostResolver;

    public PasswordController(PasswordService passwordService,
            PasswordResetRateLimiter rateLimiter,
            TenantHostResolver tenantHostResolver) {
        this.passwordService = passwordService;
        this.rateLimiter = rateLimiter;
        this.tenantHostResolver = tenantHostResolver;
    }

    @Operation(summary = "api.password.verify.summary", description = "api.password.verify.description")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "api.password.verify.200"),
            @ApiResponse(responseCode = "404", description = "api.password.verify.404"),
            @ApiResponse(responseCode = "429", description = "api.auth.login.429")
    })
    @PostMapping("/verify")
    public TokenVerifyResponse verify(@Valid @RequestBody TokenVerifyRequest request,
            HttpServletRequest httpRequest) {
        rateLimiter.checkTokenAttempt(httpRequest.getRemoteAddr());
        return passwordService.verify(request.token());
    }

    @Operation(summary = "api.password.set.summary", description = "api.password.set.description")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "api.password.set.204"),
            @ApiResponse(responseCode = "404", description = "api.password.verify.404"),
            @ApiResponse(responseCode = "429", description = "api.auth.login.429")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(@Valid @RequestBody PasswordSetRequest request, HttpServletRequest httpRequest) {
        rateLimiter.checkTokenAttempt(httpRequest.getRemoteAddr());
        passwordService.set(request.token(), request.password());
    }

    @Operation(summary = "api.password.reset-request.summary",
            description = "api.password.reset-request.description")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "api.password.reset-request.202"),
            @ApiResponse(responseCode = "400", description = "api.password.reset-request.400"),
            @ApiResponse(responseCode = "429", description = "api.auth.login.429")
    })
    @PostMapping("/reset-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resetRequest(@Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest) {
        //서브도메인/코드 병행 규칙은 로그인과 동일(D19) — AuthController.resolveTenantCode 재사용
        String tenantCode = AuthController.resolveTenantCode(
                tenantHostResolver.resolve(httpRequest), request.tenantCode());
        rateLimiter.checkResetRequest(tenantCode, request.email(), httpRequest.getRemoteAddr());
        passwordService.requestReset(tenantCode, request.email());
        //응답은 계정 존재와 무관하게 202 통일(바디 없음 — 존재 비노출)
    }

}
