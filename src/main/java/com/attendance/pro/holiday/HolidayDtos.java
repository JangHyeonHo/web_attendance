package com.attendance.pro.holiday;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 공휴일 관리 API(/api/v1/tenant/holidays) 요청/응답 DTO 모음.
 */
public final class HolidayDtos {

    private HolidayDtos() {
    }

    @Schema(description = "schema.holiday-response")
    public record HolidayResponse(
            long holidayId,
            @Schema(description = "schema.field.holiday-date") LocalDate holidayDate,
            @Schema(description = "schema.field.holiday-name") String holidayName,
            @Schema(description = "schema.holiday-type") HolidayType holidayType,
            @Schema(description = "schema.field.holiday-recurring") boolean recurring,
            LocalDateTime updatedAt) {

        public static HolidayResponse from(Holiday holiday) {
            return new HolidayResponse(holiday.holidayId(), holiday.holidayDate(), holiday.holidayName(),
                    holiday.holidayType(), holiday.recurring(), holiday.updatedAt());
        }
    }

    @Schema(description = "schema.holiday-sync-response")
    public record HolidaySyncResponse(
            int year, String country,
            @Schema(description = "schema.holiday-sync-response.fetched") int fetched,
            @Schema(description = "schema.holiday-sync-response.inserted") int inserted,
            @Schema(description = "schema.holiday-sync-response.deleted") int deleted,
            @Schema(description = "schema.holiday-sync-response.skipped-company") int skippedCompany) {
    }

    @Schema(description = "schema.holiday-create-request")
    public record HolidayCreateRequest(
            @Schema(description = "schema.field.holiday-date", example = "2026-10-01")
            @NotNull(message = "{validation.holiday-date.required}")
            LocalDate holidayDate,

            @Schema(description = "schema.field.holiday-name", example = "창립기념일")
            @NotBlank(message = "{validation.holiday-name.required}")
            @Size(max = 100, message = "{validation.holiday-name.size}")
            String holidayName,

            @Schema(description = "schema.field.holiday-recurring")
            Boolean recurring) {   //type 없음 — 수동 등록은 항상 COMPANY(동기화 불가침)

        /** null 허용(과거 클라이언트 호환) — 미지정은 반복 아님. */
        public boolean recurringFlag() {
            return Boolean.TRUE.equals(recurring);
        }
    }

    @Schema(description = "schema.holiday-update-request")
    public record HolidayUpdateRequest(
            @Schema(description = "schema.field.holiday-date", example = "2026-10-01")
            @NotNull(message = "{validation.holiday-date.required}")
            LocalDate holidayDate,

            @Schema(description = "schema.field.holiday-name", example = "창립기념일")
            @NotBlank(message = "{validation.holiday-name.required}")
            @Size(max = 100, message = "{validation.holiday-name.size}")
            String holidayName,

            @Schema(description = "schema.field.holiday-recurring")
            Boolean recurring) {   //COMPANY 전용 수정(개별 인스턴스) — 이동/명칭/반복 토글

        public boolean recurringFlag() {
            return Boolean.TRUE.equals(recurring);
        }
    }

}
