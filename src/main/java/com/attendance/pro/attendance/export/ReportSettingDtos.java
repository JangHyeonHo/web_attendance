package com.attendance.pro.attendance.export;

import jakarta.validation.constraints.NotNull;

/** 근태 보고서 설정 DTO. */
public final class ReportSettingDtos {

    private ReportSettingDtos() {
    }

    /** 조회/갱신 응답 — 결재(도장)란 표시 여부. */
    public record ReportSettingResponse(boolean stampEnabled) {
    }

    /** 갱신 요청. */
    public record ReportSettingRequest(@NotNull Boolean stampEnabled) {
    }
}
