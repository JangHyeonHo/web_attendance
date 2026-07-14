package com.attendance.pro.leave;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 휴가 API DTO 모음. 내부 수량은 분(minutes), 외부 표시는 '일(days)' 병기.
 */
public final class LeaveDtos {

    private LeaveDtos() {
    }

    // ---- 휴가 종류 ----

    public record LeaveTypeResponse(
            long leaveTypeId, String code, String name, boolean paid, LeaveUnit unit,
            boolean requiresApproval, boolean isAnnual, boolean active, int sortOrder) {

        public static LeaveTypeResponse of(LeaveType t) {
            return new LeaveTypeResponse(t.leaveTypeId(), t.code(), t.name(), t.paid(), t.unit(),
                    t.requiresApproval(), t.isAnnual(), t.active(), t.sortOrder());
        }
    }

    public record LeaveTypeCreateRequest(
            @NotBlank(message = "{validation.required}") @Size(max = 30) String code,
            @NotBlank(message = "{validation.required}") @Size(max = 50) String name,
            boolean paid,
            @NotNull(message = "{validation.required}") LeaveUnit unit,
            boolean requiresApproval,
            int sortOrder) {
    }

    public record LeaveTypeUpdateRequest(
            @NotBlank(message = "{validation.required}") @Size(max = 50) String name,
            boolean paid,
            @NotNull(message = "{validation.required}") LeaveUnit unit,
            boolean requiresApproval,
            boolean active,
            int sortOrder) {
    }

    // ---- 잔여 ----

    /** 종류별 잔여. 분 단위 + 일(days) 환산(standardDayMinutes 기준). */
    public record LeaveBalanceResponse(
            long leaveTypeId, String code, String name, LeaveUnit unit, boolean isAnnual,
            int grantedMinutes, int usedMinutes, int pendingMinutes, int remainingMinutes,
            int standardDayMinutes) {
    }

    /**
     * 만기일별 잔여 한 행 — 부여 행 하나의 남은 분과 만기일. 만기 임박순 FIFO로 사용분을 차감해 산출.
     * (예: 유급휴가 3일 2026-07-15 / 유급휴가 2일 2027-07-15). expiresOn null = 무기한.
     */
    public record LeaveBalanceRowResponse(
            long leaveTypeId, String name, LeaveUnit unit, int remainingMinutes,
            LocalDate expiresOn, int standardDayMinutes) {
    }

    // ---- 신청(멤버) ----

    /**
     * 휴가 신청. dayUnit=true면 startDate~endDate(+halfDay), false면 특정일 startTime~endTime.
     * 검증은 서비스에서(단위별 필수 필드 분기).
     */
    public record LeaveApplyRequest(
            @NotNull(message = "{validation.required}") Long leaveTypeId,
            boolean dayUnit,
            LocalDate startDate,
            LocalDate endDate,
            Boolean halfDay,
            LocalDateTime startTime,
            LocalDateTime endTime,
            @Size(max = 200) String reason) {

        /** 생략(null) 허용 — 반차 아님으로 취급. */
        public boolean isHalfDay() {
            return Boolean.TRUE.equals(halfDay);
        }
    }

    public record LeaveRequestResponse(
            long leaveRequestId, long userId, String userName, long leaveTypeId, String typeName,
            LeaveUnit unit, LocalDateTime startAt, LocalDateTime endAt, int minutes,
            boolean dayUnit, boolean halfDay, String reason, LeaveStatus status,
            LocalDateTime decidedAt, String decisionNote, String cancelReason,
            LocalDateTime createdAt) {

        public static LeaveRequestResponse of(LeaveRequestMapper.LeaveRequestView v) {
            return new LeaveRequestResponse(v.leaveRequestId(), v.userId(), v.userName(),
                    v.leaveTypeId(), v.typeName(), v.unit(), v.startAt(), v.endAt(), v.minutes(),
                    v.dayUnit(), v.halfDay(), v.reason(), v.status(), v.decidedAt(),
                    v.decisionNote(), v.cancelReason(), v.createdAt());
        }
    }

    // ---- 결재/부여(관리자) ----

    public record LeaveDecisionRequest(
            boolean approve,
            @Size(max = 200) String note) {
    }

    /** 취소 사유(필수) — 멤버 취소 신청 / 관리자 직접 취소 공통. */
    public record LeaveCancelRequest(
            @NotBlank(message = "{validation.required}") @Size(max = 200) String reason) {
    }

    /** 취소 신청 반려 메모(선택). */
    public record LeaveNoteRequest(
            @Size(max = 200) String note) {
    }

    /** 수동 부여/조정 — days(음수 가능=차감 조정). expiresOn 생략 시 무기한. */
    public record LeaveGrantRequest(
            @NotNull(message = "{validation.required}") Long userId,
            @NotNull(message = "{validation.required}") Long leaveTypeId,
            @NotNull(message = "{validation.required}") Double days,
            LocalDate expiresOn,
            @Size(max = 200) String memo) {
    }

    public record HireDateRequest(
            @NotNull(message = "{validation.required}") LocalDate hireDate) {
    }

    // ---- 관리자: 멤버 상세 ----

    public record MemberLeaveDetail(
            long userId, String name, LocalDate hireDate, int standardDayMinutes,
            Integer suggestedAnnualMinutes, List<LeaveBalanceResponse> balances,
            List<LeaveRequestResponse> requests) {
    }

    /**
     * 멤버 잔여 개요(연차 중심).
     * suggestedAnnualMinutes = 법정 제안(입사일·근속 기준 계산값 — 미리보기, 자동 부여 아님).
     * annualRemainingMinutes = 실제 부여−사용 잔여. 둘을 비교해 관리자가 "제안 적용" 여부를 판단한다.
     */
    public record MemberLeaveSummary(
            long userId, String name, LocalDate hireDate, Integer annualRemainingMinutes,
            Integer suggestedAnnualMinutes, int standardDayMinutes) {
    }
}
