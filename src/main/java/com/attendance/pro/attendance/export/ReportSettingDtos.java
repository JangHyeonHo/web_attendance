package com.attendance.pro.attendance.export;

import jakarta.validation.constraints.NotNull;

/** 근태 보고서 설정 DTO. */
public final class ReportSettingDtos {

    private ReportSettingDtos() {
    }

    /** 조회/갱신 응답 — 결재(도장)란 표시 여부 + 가산수당 적용 여부(§56). */
    public record ReportSettingResponse(boolean stampEnabled, boolean premiumEnabled) {
    }

    /** 갱신 요청 — 두 토글 모두 필수(W020 회사 설정에서 [저장] 시 함께 전송). */
    public record ReportSettingRequest(@NotNull Boolean stampEnabled, @NotNull Boolean premiumEnabled) {
    }
}
