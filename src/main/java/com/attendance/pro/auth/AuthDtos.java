package com.attendance.pro.auth;

import java.time.LocalDateTime;

import com.attendance.pro.user.MemberDtos;
import com.attendance.pro.user.Role;
import com.attendance.pro.user.TokenPurpose;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 인증 API 요청/응답 DTO 모음.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(description = "schema.login-request")
    public record LoginRequest(
            //서브도메인/코드 병행: 테넌트 서브도메인으로 접속한 경우 생략 가능(호스트가 우선).
            //루트 도메인 접속은 필수 — 조건부 검증이라 @NotBlank 대신 AuthController가 확인한다.
            @Schema(description = "schema.field.tenant-code", example = "ACME")
            @Size(max = 20, message = "{validation.tenant-code.size}")
            String tenantCode,

            @Schema(description = "schema.field.email", example = "admin@attendance.local")
            @NotBlank(message = "{validation.email.required}")
            String email,

            @Schema(description = "schema.field.password", example = "Admin123!")
            @NotBlank(message = "{validation.password.required}")
            String password) {

        @Override
        public String toString() {  //로그 유출 방지: 비밀번호는 toString에서 제외(다른 민감 DTO와 동일 규약)
            return "LoginRequest[tenantCode=%s, email=%s, password=***]".formatted(tenantCode, email);
        }
    }

    @Schema(description = "schema.login-response")
    public record LoginResponse(
            @Schema(description = "schema.field.user-id", example = "1") long userId,
            @Schema(description = "schema.field.email") String email,
            @Schema(description = "schema.field.name") String name,
            @Schema(description = "schema.field.role", example = "MEMBER") Role role,
            @Schema(description = "schema.field.tenant-code", example = "ACME") String tenantCode,
            @Schema(description = "schema.field.tenant-name", example = "에이크미(주)") String tenantName) {

        public static LoginResponse from(SessionUser user) {
            return new LoginResponse(user.userId(), user.email(), user.name(),
                    user.role(), user.tenantCode(), user.tenantName());
        }
    }

    @Schema(description = "schema.token-verify-request")
    public record TokenVerifyRequest(
            @Schema(description = "schema.field.token")
            @NotBlank(message = "{validation.token.required}")
            String token) {

        @Override
        public String toString() {  //토큰 로그 유출 방지(D-F 규약 확장)
            return "TokenVerifyRequest[token=***]";
        }
    }

    @Schema(description = "schema.token-verify-response")
    public record TokenVerifyResponse(
            @Schema(description = "schema.token-verify-response.purpose") TokenPurpose purpose, //W010 안내 분기
            @Schema(description = "schema.field.name") String name,
            @Schema(description = "schema.field.email-masked", example = "h***@acme.co.kr") String emailMasked,
            @Schema(description = "schema.field.tenant-name") String tenantName,
            @Schema(description = "schema.token-verify-response.expires-at") LocalDateTime expiresAt) {
    }

    @Schema(description = "schema.password-set-request")
    public record PasswordSetRequest(
            @Schema(description = "schema.field.token")
            @NotBlank(message = "{validation.token.required}")
            String token,

            @Schema(description = "schema.field.password")
            @NotBlank(message = "{validation.password.required}")
            @Pattern(regexp = MemberDtos.PASSWORD_PATTERN, message = "{validation.password.pattern}")
            String password) {

        @Override
        public String toString() {  //토큰·비밀번호 로그 유출 방지
            return "PasswordSetRequest[token=***, password=***]";
        }
    }

    @Schema(description = "schema.password-reset-request")
    public record PasswordResetRequest(
            //서브도메인 접속이면 생략 가능(호스트 우선 — D19), 루트 접속은 필수(컨트롤러 확인)
            @Schema(description = "schema.field.tenant-code", example = "ACME")
            @Size(max = 20, message = "{validation.tenant-code.size}")
            String tenantCode,

            @Schema(description = "schema.field.email", example = "hong@acme.co.kr")
            @NotBlank(message = "{validation.email.required}")
            String email) {
    }

}
