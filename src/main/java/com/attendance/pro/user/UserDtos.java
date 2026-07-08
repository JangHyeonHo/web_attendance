package com.attendance.pro.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원 API 요청/응답 DTO 모음.
 */
public final class UserDtos {

    /** 영문 대소문자/숫자/특수문자 중 3종 이상 조합 8~30자 */
    public static final String PASSWORD_PATTERN =
            "^((?=.*[\\d])(?=.*[a-z])(?=.*[A-Z])|(?=.*[a-z])(?=.*[A-Z])(?=.*[^\\w\\d\\s])|(?=.*[\\d])(?=.*[A-Z])(?=.*[^\\w\\d\\s])|(?=.*[\\d])(?=.*[a-z])(?=.*[^\\w\\d\\s])).{8,30}$";

    private UserDtos() {
    }

    @Schema(description = "회원가입 요청")
    public record SignupRequest(
            @Schema(description = "이메일(로그인 ID)", example = "hong@example.com")
            @NotBlank(message = "{validation.email.required}")
            @jakarta.validation.constraints.Email(message = "{validation.email.format}")
            @Size(max = 100, message = "{validation.email.size}")
            String email,

            @Schema(description = "비밀번호(영문 대소문자/숫자/특수문자 중 3종 이상 조합 8~30자)", example = "Passw0rd!")
            @NotBlank(message = "{validation.password.required}")
            @Pattern(regexp = PASSWORD_PATTERN,
                    message = "{validation.password.pattern}")
            String password,

            @Schema(description = "이름", example = "홍길동")
            @NotBlank(message = "{validation.name.required}")
            @Size(max = 50, message = "{validation.name.size}")
            String name,

            @Schema(description = "부서 코드(선택)", example = "DEV01")
            @Size(max = 50, message = "{validation.depart.size}")
            String departCd) {
    }

    @Schema(description = "회원 정보 응답")
    public record UserResponse(
            @Schema(description = "유저 ID", example = "1") long userId,
            @Schema(description = "이메일", example = "hong@example.com") String email,
            @Schema(description = "이름", example = "홍길동") String name,
            @Schema(description = "부서 코드", example = "DEV01") String departCd,
            @Schema(description = "관리자 여부", example = "false") boolean admin) {

        public static UserResponse from(User user) {
            return new UserResponse(user.userId(), user.email(), user.name(), user.departCd(), user.admin());
        }
    }

}
