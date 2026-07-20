package com.attendance.pro.attendance.close;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 근태 마감 API DTO 모음. */
public final class AttendanceCloseDtos {

    private AttendanceCloseDtos() {
    }

    /** 마감 신청(멤버) — 대상 연·월. */
    public record CloseRequest(
            @NotNull(message = "{validation.year.required}") @Min(2000) @Max(2100) Integer year,
            @NotNull(message = "{validation.month.required}") @Min(1) @Max(12) Integer month) {
    }

    /** 결재(인사관리자) — 승인/반려 + 메모(반려 사유 등). */
    public record CloseDecisionRequest(
            @NotNull(message = "{validation.close.approve.required}") Boolean approve,
            @Size(max = 200, message = "{validation.close.note.size}") String note) {
    }

    /**
     * 마감 상태(멤버 근무표에서 버튼 상태 판단). status=null이면 신청 이력 없음.
     * canRequest = 대상 월 종료 && (미신청 || 반려) → 신청 가능.
     */
    public record CloseStatusResponse(
            int year, int month, AttendanceCloseStatus status, Long closeId,
            boolean canRequest, boolean monthEnded,
            LocalDateTime requestedAt, LocalDateTime decidedAt, String decisionNote) {
    }

    /** 결재 대기 1건(관리자 목록). */
    public record PendingCloseResponse(
            long closeId, long userId, String userName,
            int year, int month, LocalDateTime requestedAt) {
    }
}
