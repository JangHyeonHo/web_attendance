package com.attendance.pro.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 인증 API 요청/응답 DTO 모음.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(description = "schema.login-request")
    public record LoginRequest(
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
            @Schema(description = "schema.field.email", example = "admin@attendance.local") String email,
            @Schema(description = "schema.field.name", example = "관리자") String name,
            @Schema(description = "schema.field.admin", example = "true") boolean admin) {

        public static LoginResponse from(SessionUser user) {
            return new LoginResponse(user.userId(), user.email(), user.name(), user.admin());
        }
    }

}
