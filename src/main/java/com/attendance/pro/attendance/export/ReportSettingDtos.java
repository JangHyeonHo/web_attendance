package com.attendance.pro.attendance.export;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 근태 보고서 설정 DTO. */
public final class ReportSettingDtos {

    private ReportSettingDtos() {
    }

    /**
     * 조회/갱신 응답 — 결재란 표시·가산 적용·도장 크기·도장 이미지(data URL, 미등록 null).
     * stampImageUrl은 회사 도장(비밀 아님)으로, 멤버 인쇄 시 결재란 날인에 쓰인다.
     */
    public record ReportSettingResponse(boolean stampEnabled, boolean premiumEnabled,
            String stampSize, String stampImageUrl) {
    }

    /** 갱신 요청 — 토글 2종 + 도장 크기(W020 [저장] 시 함께 전송). 이미지는 별도 엔드포인트. */
    public record ReportSettingRequest(@NotNull Boolean stampEnabled, @NotNull Boolean premiumEnabled,
            String stampSize) {
    }

    /** 도장 이미지 업로드 — base64(data URL 허용) + MIME. 검증(크기·형식)은 서비스에서. */
    public record StampImageRequest(
            @NotBlank(message = "{report.stamp.invalid}") @Size(max = 400_000) String imageBase64,
            @NotBlank(message = "{report.stamp.invalid}") String mime) {
    }
}
