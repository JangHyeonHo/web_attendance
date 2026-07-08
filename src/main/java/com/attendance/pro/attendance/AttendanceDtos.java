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
            @Schema(description = "schema.status-response.alert-label") String alertLabel) {
    }

    @Schema(description = "schema.daily-attendance")
    public record DailyAttendance(
            @Schema(description = "schema.daily-attendance.date") LocalDate date,
            @Schema(description = "schema.daily-attendance.holiday") boolean holiday,
            @Schema(description = "schema.daily-attendance.schedule-start", example = "09:00") String scheduleStart,
            @Schema(description = "schema.daily-attendance.schedule-end", example = "18:00") String scheduleEnd,
            @Schema(description = "schema.daily-attendance.stamp-in", example = "09:12") String stampIn,
            @Schema(description = "schema.daily-attendance.stamp-out", example = "18:03") String stampOut) {
    }

    @Schema(description = "schema.monthly-response")
    public record MonthlyResponse(
            @Schema(description = "schema.field.year", example = "2026") int year,
            @Schema(description = "schema.field.month", example = "7") int month,
            @Schema(description = "schema.monthly-response.days") List<DailyAttendance> days) {
    }

}
