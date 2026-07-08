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
            @NotBlank(message = "이메일을 입력해 주세요.")
            @jakarta.validation.constraints.Email(message = "이메일 형식이 아닙니다.")
            @Size(max = 100, message = "이메일은 100자 이하로 해주세요.")
            String email,

            @Schema(description = "비밀번호(영문 대소문자/숫자/특수문자 중 3종 이상 조합 8~30자)", example = "Passw0rd!")
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Pattern(regexp = PASSWORD_PATTERN,
                    message = "비밀번호는 영문자, 특수문자, 숫자를 포함하여 8자 이상, 30글자 이하로 해주세요.")
            String password,

            @Schema(description = "이름", example = "홍길동")
            @NotBlank(message = "이름을 입력해 주세요.")
            @Size(max = 50, message = "이름은 50자 이하로 해주세요.")
            String name,

            @Schema(description = "부서 코드(선택)", example = "DEV01")
            @Size(max = 50, message = "부서 코드는 50자 이하로 해주세요.")
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
