package com.attendance.pro.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.AuthDtos.LoginRequest;
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
@Tag(name = "Auth", description = "로그인/로그아웃 API (세션 쿠키 기반)")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하고 세션을 발급한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "이메일 혹은 비밀번호 불일치")
    })
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        SessionUser user = authService.authenticate(request.email(), request.password());
        //세션 고정 공격 방지를 위해 기존 세션을 무효화하고 새로 발급한다.
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        httpRequest.getSession(true).setAttribute(SessionUser.SESSION_KEY, user);
        return LoginResponse.from(user);
    }

    @Operation(summary = "로그아웃", description = "현재 세션을 무효화한다.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Operation(summary = "내 정보", description = "현재 로그인한 유저 정보를 돌려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "미로그인")
    })
    @GetMapping("/me")
    public LoginResponse me(@LoginUser SessionUser user) {
        return LoginResponse.from(user);
    }

}
