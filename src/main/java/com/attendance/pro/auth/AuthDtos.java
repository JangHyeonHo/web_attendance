package com.attendance.pro.auth;

import com.attendance.pro.user.Role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

}
