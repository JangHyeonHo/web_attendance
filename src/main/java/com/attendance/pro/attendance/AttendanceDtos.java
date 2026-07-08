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

    @Schema(description = "출결 체크 요청(확정 전 사전 검사)")
    public record CheckRequest(
            @Schema(description = "출결 타입") @NotNull(message = "출결 타입을 입력해 주세요.") AttendanceType type,
            @Schema(description = "위도", example = "37.5665000") Double latitude,
            @Schema(description = "경도", example = "126.9780000") Double longitude,
            @Schema(description = "장소 정보", example = "서울시 중구") @Size(max = 200) String placeInfo,
            @Schema(description = "단말 정보", example = "Chrome/Windows") @Size(max = 100) String terminal) {
    }

    @Schema(description = "출결 체크 응답")
    public record CheckResponse(
            @Schema(description = "확정 가능 여부") boolean allowed,
            @Schema(description = "사용자 확인(덮어쓰기/재출근) 필요 여부") boolean requiresConfirmation,
            @Schema(description = "체크 결과 코드(문제 없으면 null)") ConfirmCode code,
            @Schema(description = "표시 메시지(문제 없으면 null)") String message,
            @Schema(description = "확정 요청에 사용할 토큰(확정 가능할 때만 발급)") String token) {

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

    @Schema(description = "출결 확정 요청(체크에서 받은 토큰 + 체크와 동일한 데이터)")
    public record ConfirmRequest(
            @Schema(description = "체크 응답으로 받은 토큰") @NotBlank(message = "토큰을 입력해 주세요.") String token,
            @Schema(description = "출결 타입") @NotNull(message = "출결 타입을 입력해 주세요.") AttendanceType type,
            @Schema(description = "위도", example = "37.5665000") Double latitude,
            @Schema(description = "경도", example = "126.9780000") Double longitude,
            @Schema(description = "장소 정보", example = "서울시 중구") @Size(max = 200) String placeInfo,
            @Schema(description = "단말 정보", example = "Chrome/Windows") @Size(max = 100) String terminal) {

        public CheckRequest toCheckRequest() {
            return new CheckRequest(type, latitude, longitude, placeInfo, terminal);
        }
    }

    @Schema(description = "출결 확정 응답")
    public record StampResponse(
            @Schema(description = "출결 타입") AttendanceType type,
            @Schema(description = "스탬프 시각") LocalDateTime stampedAt,
            @Schema(description = "표시 메시지", example = "현재 시간 09:00에 출근 하셨습니다.") String message) {
    }

    @Schema(description = "현재 출결 상태", enumAsRef = true)
    public enum WorkStatus {
        /** 출근 대기 */
        WAITING("출근 대기"),
        /** 출근 중 */
        WORKING("출근 중"),
        /** 퇴근 완료 */
        OFF_WORK_DONE("퇴근 완료"),
        /** 조퇴 완료 */
        EARLY_DEPARTURE_DONE("조퇴 완료"),
        /** 출근 중(휴식) */
        ON_BREAK("출근 중(휴식)"),
        /** 출근 중(휴식 완료) */
        BREAK_ENDED("출근 중(휴식 완료)");

        private final String label;

        WorkStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Schema(description = "출결 상태 알림", enumAsRef = true)
    public enum StatusAlert {
        /** 출근한지 하루 경과 - 퇴근 필요 */
        OVERDUE_OFF_WORK("출근한지 하루가 지났습니다! 퇴근을 찍어주세요"),
        /** 휴식한지 하루 경과 - 휴식 완료 필요 */
        OVERDUE_BREAK_END("휴식한지 하루가 지났습니다! 휴식 완료를 찍어주세요");

        private final String label;

        StatusAlert(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Schema(description = "출결 상태 응답")
    public record StatusResponse(
            @Schema(description = "현재 상태") WorkStatus status,
            @Schema(description = "상태 표시 텍스트", example = "출근 중") String statusLabel,
            @Schema(description = "기준 스탬프 시각(대기 상태면 null)") LocalDateTime stampedAt,
            @Schema(description = "알림(없으면 null)") StatusAlert alert,
            @Schema(description = "알림 표시 텍스트(없으면 null)") String alertLabel) {

        public static StatusResponse of(WorkStatus status, LocalDateTime stampedAt, StatusAlert alert) {
            return new StatusResponse(status, status.label(), stampedAt,
                    alert, alert == null ? null : alert.label());
        }
    }

    @Schema(description = "일별 출결(월별 상세의 한 행)")
    public record DailyAttendance(
            @Schema(description = "날짜") LocalDate date,
            @Schema(description = "휴일 여부(공휴일/개인휴일)") boolean holiday,
            @Schema(description = "스케쥴 시업 시각", example = "09:00") String scheduleStart,
            @Schema(description = "스케쥴 종업 시각", example = "18:00") String scheduleEnd,
            @Schema(description = "출근 시각(미출근이면 null)", example = "09:12") String stampIn,
            @Schema(description = "퇴근 시각(미퇴근이면 null, 자정 넘긴 퇴근은 24+시간, 예: 25:10)", example = "18:03") String stampOut) {
    }

    @Schema(description = "월별 출결 상세 응답")
    public record MonthlyResponse(
            @Schema(description = "연도", example = "2026") int year,
            @Schema(description = "월(1~12)", example = "7") int month,
            @Schema(description = "일별 출결") List<DailyAttendance> days) {
    }

}
