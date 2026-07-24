package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 출결 API 요청/응답 DTO 모음.
 */
public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    @Schema(description = "schema.check-request")
    public record CheckRequest(
            @Schema(description = "schema.attendance-type") @NotNull(message = "{validation.attendance.type.required}") AttendanceType type,
            @Schema(description = "schema.field.latitude", example = "37.5665000") Double latitude,
            @Schema(description = "schema.field.longitude", example = "126.9780000") Double longitude,
            @Schema(description = "schema.field.place-info", example = "서울시 중구") @Size(max = 200) String placeInfo,
            @Schema(description = "schema.field.terminal", example = "Chrome/Windows") @Size(max = 100) String terminal) {
    }

    @Schema(description = "schema.check-response")
    public record CheckResponse(
            @Schema(description = "schema.check-response.allowed") boolean allowed,
            @Schema(description = "schema.check-response.requires-confirmation") boolean requiresConfirmation,
            @Schema(description = "schema.check-response.code") ConfirmCode code,
            @Schema(description = "schema.check-response.message") String message,
            @Schema(description = "schema.check-response.token") String token) {

        public static CheckResponse ok(String token) {
            return new CheckResponse(true, false, null, null, token);
        }

        public static CheckResponse needsConfirmation(ConfirmCode code, String message, String token) {
            return new CheckResponse(true, true, code, message, token);
        }

        public static CheckResponse rejected(ConfirmCode code, String message) {
            return new CheckResponse(false, false, code, message, null);
        }
    }

    @Schema(description = "schema.confirm-request")
    public record ConfirmRequest(
            @Schema(description = "schema.confirm-request.token") @NotBlank(message = "{validation.attendance.token.required}") String token,
            @Schema(description = "schema.attendance-type") @NotNull(message = "{validation.attendance.type.required}") AttendanceType type,
            @Schema(description = "schema.field.latitude", example = "37.5665000") Double latitude,
            @Schema(description = "schema.field.longitude", example = "126.9780000") Double longitude,
            @Schema(description = "schema.field.place-info", example = "서울시 중구") @Size(max = 200) String placeInfo,
            @Schema(description = "schema.field.terminal", example = "Chrome/Windows") @Size(max = 100) String terminal) {

        public CheckRequest toCheckRequest() {
            return new CheckRequest(type, latitude, longitude, placeInfo, terminal);
        }
    }

    /** 자동 스탬프 비고 작성/수정 요청 — 비고만 갱신(시각·구분·위치 불변). */
    @Schema(description = "schema.stamp-note-request")
    public record StampNoteRequest(
            @Schema(description = "schema.field.stamp-note", example = "실수로 중복 등록 — 이전 기록이 잘못 찍은 것") @Size(max = 200) String note) {
    }

    //수동 정정 등록(Phase 5) — 사유 필수(선택 코드 + OTHER는 자유 텍스트 필수). BREAK는 대상 외
    @Schema(description = "schema.manual-stamp-request")
    public record ManualStampRequest(
            @Schema(description = "schema.manual-stamp-request.date", example = "2026-07-09")
            @NotNull(message = "{validation.attendance.date.required}") LocalDate date,

            @Schema(description = "schema.manual-stamp-request.time", example = "09:00")
            @NotBlank(message = "{validation.work-time.required}")
            @jakarta.validation.constraints.Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                    message = "{validation.work-time.format}")
            String time,

            @Schema(description = "schema.attendance-type")
            @NotNull(message = "{validation.attendance.type.required}") AttendanceType type,

            @Schema(description = "schema.manual-stamp-request.reason-code", example = "FORGOT")
            @NotBlank(message = "{validation.attendance.reason.required}") String reasonCode,

            @Schema(description = "schema.manual-stamp-request.reason-text", example = "출근 시 단말 미지참")
            @Size(max = 200, message = "{validation.attendance.reason.size}") String reasonText) {
    }

    //휴식 시간 수동 정정(Phase 5.3) — 시작·종료를 쌍으로 등록(단일 스탬프 정합성 문제 회피).
    //등록만 이 요청, 시각 정정은 개별 휴식 스탬프의 updateManual(시각·사유만)로 처리한다.
    @Schema(description = "schema.manual-break-request")
    public record ManualBreakRequest(
            @Schema(description = "schema.manual-stamp-request.date", example = "2026-07-09")
            @NotNull(message = "{validation.attendance.date.required}") LocalDate date,

            @Schema(description = "schema.manual-break-request.start", example = "12:00")
            @NotBlank(message = "{validation.work-time.required}")
            @jakarta.validation.constraints.Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                    message = "{validation.work-time.format}")
            String startTime,

            @Schema(description = "schema.manual-break-request.end", example = "13:00")
            @NotBlank(message = "{validation.work-time.required}")
            @jakarta.validation.constraints.Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                    message = "{validation.work-time.format}")
            String endTime,

            @Schema(description = "schema.manual-stamp-request.reason-code", example = "FORGOT")
            @NotBlank(message = "{validation.attendance.reason.required}") String reasonCode,

            @Schema(description = "schema.manual-stamp-request.reason-text", example = "휴식 종료 미기록")
            @Size(max = 200, message = "{validation.attendance.reason.size}") String reasonText) {
    }

    /** 일자 스탬프 이력 1건 — attendance는 append-only라 중복 스탬프(출근 2번 등)도 전부 나온다 */
    @Schema(description = "schema.daily-stamp")
    public record DailyStampEntry(
            //수동 정정 삭제(잘못 입력 복구)의 대상 식별자 — MANUAL 행만 삭제 가능
            @Schema(description = "schema.daily-stamp.attendance-id") long attendanceId,
            @Schema(description = "schema.stamp-response.stamped-at") LocalDateTime stampedAt,
            @Schema(description = "schema.attendance-type") AttendanceType type,
            @Schema(description = "schema.daily-stamp.break-end") boolean breakEnd,
            @Schema(description = "schema.daily-stamp.source") StampSource source,
            @Schema(description = "schema.daily-stamp.reason-code") String reasonCode,
            @Schema(description = "schema.daily-stamp.reason-text") String reasonText) {
    }

    @Schema(description = "schema.daily-response")
    public record DailyResponse(
            @Schema(description = "schema.daily-attendance.date") LocalDate date,
            @Schema(description = "schema.daily-response.stamps") List<DailyStampEntry> stamps) {
    }

    @Schema(description = "schema.stamp-response")
    public record StampResponse(
            @Schema(description = "schema.attendance-type") AttendanceType type,
            @Schema(description = "schema.stamp-response.stamped-at") LocalDateTime stampedAt,
            @Schema(description = "schema.stamp-response.message", example = "현재 시간 09:00에 출근 하셨습니다.") String message) {
    }

    @Schema(description = "schema.work-status", enumAsRef = true)
    public enum WorkStatus {
        /** 출근 대기 */
        WAITING,
        /** 출근 중 */
        WORKING,
        /** 퇴근 완료 */
        OFF_WORK_DONE,
        /** 조퇴 완료 */
        EARLY_DEPARTURE_DONE,
        /** 출근 중(휴식) */
        ON_BREAK,
        /** 출근 중(휴식 완료) */
        BREAK_ENDED;

        /** 표시 텍스트의 메시지 키 */
        public String labelKey() {
            return "status." + name();
        }
    }

    @Schema(description = "schema.status-alert", enumAsRef = true)
    public enum StatusAlert {
        /** 출근한지 하루 경과 - 퇴근 필요 */
        OVERDUE_OFF_WORK,
        /** 휴식한지 하루 경과 - 휴식 완료 필요 */
        OVERDUE_BREAK_END;

        /** 표시 텍스트의 메시지 키 */
        public String labelKey() {
            return "alert." + name();
        }
    }

    @Schema(description = "schema.status-response")
    public record StatusResponse(
            @Schema(description = "schema.status-response.status") WorkStatus status,
            @Schema(description = "schema.status-response.status-label", example = "출근 중") String statusLabel,
            @Schema(description = "schema.status-response.stamped-at") LocalDateTime stampedAt,
            @Schema(description = "schema.status-response.alert") StatusAlert alert,
            @Schema(description = "schema.status-response.alert-label") String alertLabel,
            //오늘의 해석된 스케줄(우선순위: work_schedule > 개인 기본값 > 상수). 휴일이면 null
            @Schema(description = "schema.status-response.today-schedule-start", example = "09:00")
            String todayScheduleStart,
            @Schema(description = "schema.status-response.today-schedule-end", example = "18:00")
            String todayScheduleEnd) {
    }

    //현행 10필드(CR3-7) + Phase 5: dayOff(요일 휴무)·manual(수동 정정 존재) = 12필드
    @Schema(description = "schema.daily-attendance")
    public record DailyAttendance(
            @Schema(description = "schema.daily-attendance.date") LocalDate date,
            @Schema(description = "schema.daily-attendance.holiday") boolean holiday,
            //휴무일(실효 스케줄 OFF 또는 스케줄 미설정). holiday와 상호배타
            @Schema(description = "schema.daily-attendance.day-off") boolean dayOff,
            @Schema(description = "schema.daily-attendance.schedule-start", example = "09:00") String scheduleStart,
            @Schema(description = "schema.daily-attendance.schedule-end", example = "18:00") String scheduleEnd,
            @Schema(description = "schema.daily-attendance.stamp-in", example = "09:12") String stampIn,
            @Schema(description = "schema.daily-attendance.stamp-out", example = "18:03") String stampOut,
            //공휴일 명칭(개인 휴일·근무일은 null — 프론트는 holidayName ?? t('HOLIDAY') 폴백)
            @Schema(description = "schema.daily-attendance.holiday-name", example = "삼일절") String holidayName,
            @Schema(description = "schema.daily-attendance.scheduled-minutes", example = "480")
            Integer scheduledMinutes,      //예정 근무(분) = 스케줄 구간 − 법정휴게. 휴일·휴무는 null
            @Schema(description = "schema.daily-attendance.break-minutes", example = "70")
            Integer breakMinutes,          //실휴식 합(분). 출근·퇴근 미확정이면 null
            @Schema(description = "schema.daily-attendance.statutory-break-minutes", example = "60")
            Integer statutoryBreakMinutes, //법정휴게(분). 근무일=스케줄 기반, 휴일·휴무 근무=실체류 기반, 그 외 null
            //인정 휴게(분) = max(법정, 실휴식) — 총근무에서 실제 차감되는 값(§req2).
            //휴식 미기록이어도 근무일이면 법정휴게로 자동 인정. 출근·퇴근 미확정이면 null
            @Schema(description = "schema.daily-attendance.recognized-break-minutes", example = "60")
            Integer recognizedBreakMinutes,
            @Schema(description = "schema.daily-attendance.work-minutes", example = "470")
            Integer workMinutes,           //총 근무시간(분). 출근·퇴근 미확정이면 null
            //그 날에 수동 정정 스탬프가 존재하는가(상세는 daily API — 테이블에는 마커만)
            @Schema(description = "schema.daily-attendance.manual") boolean manual,
            //그 날 정정 사유(비고) — 수동 정정 스탬프의 사유(텍스트/코드) 결합. 없으면 null
            @Schema(description = "schema.daily-attendance.note", example = "미기록") String note,
            //승인된 휴가 표시(#9) — 그 날 유효 휴가 명칭(시간 휴가는 시각 접미 표기). 없으면 null
            @Schema(description = "schema.daily-attendance.leave-name", example = "유급휴가") String leaveName) {
    }

    @Schema(description = "schema.monthly-response")
    public record MonthlyResponse(
            @Schema(description = "schema.field.year", example = "2026") int year,
            @Schema(description = "schema.field.month", example = "7") int month,
            @Schema(description = "schema.monthly-response.days") List<DailyAttendance> days,
            @Schema(description = "schema.monthly-response.total-scheduled-minutes", example = "10080")
            int totalScheduledMinutes,     //월 예정근무 합 = scheduledMinutes non-null 합
            @Schema(description = "schema.monthly-response.total-break-minutes", example = "1200")
            int totalBreakMinutes,         //월 인정휴게 합 = recognizedBreakMinutes non-null 합
            @Schema(description = "schema.monthly-response.total-work-minutes", example = "9600")
            int totalWorkMinutes) {        //월 실근무 합 = workMinutes non-null 합
    }

    /**
     * 급여 정산(참고) — 월 기본급 기준의 가감 명세. 실지급이 아닌 근태 기반 참고값(4대보험·세금·수당 별도).
     * salary 미입력이면 이 응답 없음(available=false). 금액은 원/円 정수.
     */
    @Schema(description = "schema.payroll-settlement")
    public record PayrollSettlement(
            @Schema(description = "schema.payroll.country", example = "KR") String country,
            @Schema(description = "schema.payroll.base-salary", example = "3000000") long baseMonthlySalary,
            @Schema(description = "schema.payroll.hourly-wage", example = "14354") long hourlyWage,
            @Schema(description = "schema.payroll.premium-applied") boolean premiumApplied,
            @Schema(description = "schema.payroll.overtime-min", example = "180") int overtimeMinutes,
            @Schema(description = "schema.payroll.night-min", example = "60") int nightMinutes,
            @Schema(description = "schema.payroll.holiday-min", example = "0") int holidayWorkMinutes,
            @Schema(description = "schema.payroll.shortfall-min", example = "30") int shortfallMinutes,
            @Schema(description = "schema.payroll.overtime-pay", example = "64593") long overtimePay,
            @Schema(description = "schema.payroll.night-pay", example = "7177") long nightPay,
            @Schema(description = "schema.payroll.holiday-pay", example = "0") long holidayPay,
            @Schema(description = "schema.payroll.deduction", example = "7177") long deduction,
            @Schema(description = "schema.payroll.net-adjustment", example = "64593") long netAdjustment) {
    }

    /** 급여 정산 조회 응답 — 월 기본급 미입력이면 available=false, settlement=null. */
    @Schema(description = "schema.payroll-response")
    public record PayrollResponse(
            @Schema(description = "schema.payroll.available") boolean available,
            @Schema(description = "schema.payroll-settlement") PayrollSettlement settlement) {

        public static PayrollResponse of(PayrollSettlement s) {
            return new PayrollResponse(s != null, s);
        }
    }

}
