package com.attendance.pro.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.AuthDtos.LoginRequest;
import com.attendance.pro.config.LocaleConfig;
import com.attendance.pro.auth.AuthDtos.LoginResponse;

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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "api.auth.login.summary", description = "api.auth.login.description")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "api.auth.login.200"),
            @ApiResponse(responseCode = "401", description = "api.auth.login.401")
    })
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        SessionUser user = authService.authenticate(request.email(), request.password());
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
        return LoginResponse.from(user);
    }

    @Operation(summary = "api.auth.logout.summary", description = "api.auth.logout.description")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
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
