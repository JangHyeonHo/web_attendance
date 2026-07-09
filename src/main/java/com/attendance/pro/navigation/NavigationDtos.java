package com.attendance.pro.navigation;

import java.util.Map;

import com.attendance.pro.user.Role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * 화면 전개(내비게이션) API DTO 모음.
 */
public final class NavigationDtos {

    private NavigationDtos() {
    }

    @Schema(description = "schema.navigate-request")
    public record NavigateRequest(
            @Schema(description = "schema.navigate-request.screen", example = "W005")
            String screen,

            @Schema(description = "schema.navigate-request.lang", example = "KOR")
            @Pattern(regexp = "KOR|ENG|JPN", message = "{validation.lang.supported}")
            String lang) {
    }

    @Schema(description = "schema.navigation-reason", enumAsRef = true)
    public enum NavigationReason {
        /** 로그인이 필요한 화면 → 로그인 화면으로 */
        LOGIN_REQUIRED,
        /** 허용 role 집합 미포함 → 각자의 홈으로 */
        ROLE_DENIED,
        /** 이미 로그인 상태에서 로그인/홈 요청 → 홈 화면으로 */
        ALREADY_LOGGED_IN,
        /** 로그아웃 처리 완료 → 로그인 화면으로 */
        LOGGED_OUT,
        /** 알 수 없는 화면 코드 → 인덱스로 */
        UNKNOWN_SCREEN
    }

    @Schema(description = "schema.navigate-response")
    public record NavigateResponse(
            @Schema(description = "schema.navigate-response.screen", example = "W005") String screen,
            @Schema(description = "schema.navigate-response.lang", example = "KOR") String lang,
            @Schema(description = "schema.navigate-response.reason") NavigationReason reason,
            @Schema(description = "schema.navigate-response.user-name", example = "홍길동") String userName,
            @Schema(description = "schema.navigate-response.role") Role role,
            @Schema(description = "schema.navigate-response.host-tenant", example = "에이크미(주)") String hostTenantName,
            @Schema(description = "schema.navigate-response.texts") Map<String, String> texts,
            @Schema(description = "schema.navigate-response.headers") Map<String, String> headers,
            @Schema(description = "schema.navigate-response.data") Object data) {
    }

}
