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

    //통합 최종 record = 현행 6필드 + holidayName + 휴게 3필드 = 10필드(CR3-7)
    @Schema(description = "schema.daily-attendance")
    public record DailyAttendance(
            @Schema(description = "schema.daily-attendance.date") LocalDate date,
            @Schema(description = "schema.daily-attendance.holiday") boolean holiday,
            @Schema(description = "schema.daily-attendance.schedule-start", example = "09:00") String scheduleStart,
            @Schema(description = "schema.daily-attendance.schedule-end", example = "18:00") String scheduleEnd,
            @Schema(description = "schema.daily-attendance.stamp-in", example = "09:12") String stampIn,
            @Schema(description = "schema.daily-attendance.stamp-out", example = "18:03") String stampOut,
            //공휴일 명칭(개인 휴일·근무일은 null — 프론트는 holidayName ?? t('HOLIDAY') 폴백)
            @Schema(description = "schema.daily-attendance.holiday-name", example = "삼일절") String holidayName,
            @Schema(description = "schema.daily-attendance.break-minutes", example = "70")
            Integer breakMinutes,          //실휴식 합(분). 출근·퇴근 미확정이면 null
            @Schema(description = "schema.daily-attendance.statutory-break-minutes", example = "60")
            Integer statutoryBreakMinutes, //법정휴게(분). 휴일이면 null, 근무일은 항상 산출(스케줄 기반)
            @Schema(description = "schema.daily-attendance.work-minutes", example = "470")
            Integer workMinutes) {         //총 근무시간(분). 출근·퇴근 미확정이면 null
    }

    @Schema(description = "schema.monthly-response")
    public record MonthlyResponse(
            @Schema(description = "schema.field.year", example = "2026") int year,
            @Schema(description = "schema.field.month", example = "7") int month,
            @Schema(description = "schema.monthly-response.days") List<DailyAttendance> days,
            @Schema(description = "schema.monthly-response.total-work-minutes", example = "9600")
            int totalWorkMinutes) {        //월 합계 = workMinutes non-null 합
    }

}
