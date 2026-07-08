package com.attendance.pro.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 인증 API 요청/응답 DTO 모음.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(description = "로그인 요청")
    public record LoginRequest(
            @Schema(description = "이메일", example = "admin@attendance.local")
            @NotBlank(message = "이메일을 입력해 주세요.")
            String email,

            @Schema(description = "비밀번호", example = "Admin123!")
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            String password) {
    }

    @Schema(description = "로그인 유저 응답")
    public record LoginResponse(
            @Schema(description = "유저 ID", example = "1") long userId,
            @Schema(description = "이메일", example = "admin@attendance.local") String email,
            @Schema(description = "이름", example = "관리자") String name,
            @Schema(description = "관리자 여부", example = "true") boolean admin) {

        public static LoginResponse from(SessionUser user) {
            return new LoginResponse(user.userId(), user.email(), user.name(), user.admin());
        }
    }

}
