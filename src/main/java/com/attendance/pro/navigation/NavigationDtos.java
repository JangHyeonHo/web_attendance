package com.attendance.pro.navigation;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * 화면 전개(내비게이션) API DTO 모음.
 */
public final class NavigationDtos {

    private NavigationDtos() {
    }

    @Schema(description = "화면 전개 요청")
    public record NavigateRequest(
            @Schema(description = "요청 화면 코드(W000~). 미지정/미지원 코드는 인덱스로 처리", example = "W005")
            String screen,

            @Schema(description = "언어(KOR/ENG). 지정시 세션에 저장되어 이후 요청에도 적용", example = "KOR")
            @Pattern(regexp = "KOR|ENG", message = "언어는 KOR 또는 ENG만 지원합니다.")
            String lang) {
    }

    @Schema(description = "화면 전환 사유", enumAsRef = true)
    public enum NavigationReason {
        /** 로그인이 필요한 화면 → 로그인 화면으로 */
        LOGIN_REQUIRED,
        /** 관리자 전용 화면 → 권한 없음 */
        ADMIN_ONLY,
        /** 이미 로그인 상태에서 로그인/가입/홈 요청 → 출결 화면으로 */
        ALREADY_LOGGED_IN,
        /** 로그아웃 처리 완료 → 로그인 화면으로 */
        LOGGED_OUT,
        /** 알 수 없는 화면 코드 → 인덱스로 */
        UNKNOWN_SCREEN
    }

    @Schema(description = "화면 전개 응답. 서버가 결정한 '실제 표시할 화면'과 화면 데이터를 돌려준다.")
    public record NavigateResponse(
            @Schema(description = "표시할 화면 코드(요청과 다를 수 있음)", example = "W005") String screen,
            @Schema(description = "요청 화면과 다른 화면이 결정된 경우 그 사유(그대로면 null)") NavigationReason reason,
            @Schema(description = "로그인 유저 이름(미로그인이면 null)", example = "홍길동") String userName,
            @Schema(description = "화면 다국어 텍스트(key → value)") Map<String, String> texts,
            @Schema(description = "공통(헤더) 다국어 텍스트") Map<String, String> headers,
            @Schema(description = "화면 초기 데이터(출결 화면이면 출결 상태 등, 없으면 null)") Object data) {
    }

}
