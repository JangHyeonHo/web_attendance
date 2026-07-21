package com.attendance.pro.user;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 멤버 관리 API(/api/v1/tenant/members) 요청/응답 DTO 모음.
 */
public final class MemberDtos {

    /** 영문 대소문자/숫자/특수문자 중 3종 이상 조합 8~30자 (구 UserDtos에서 이관) */
    public static final String PASSWORD_PATTERN =
            "^((?=.*[\\d])(?=.*[a-z])(?=.*[A-Z])|(?=.*[a-z])(?=.*[A-Z])(?=.*[^\\w\\d\\s])|(?=.*[\\d])(?=.*[A-Z])(?=.*[^\\w\\d\\s])|(?=.*[\\d])(?=.*[a-z])(?=.*[^\\w\\d\\s])).{8,30}$";

    /** "HH:mm" — DailyAttendance 표기와 동일 포맷 */
    public static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private MemberDtos() {
    }

    static String formatTime(LocalTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }

    @Schema(description = "schema.member-create-request")
    public record MemberCreateRequest(
            @Schema(description = "schema.field.email", example = "hong@acme.co.kr")
            @NotBlank(message = "{validation.email.required}")
            @Email(message = "{validation.email.format}")
            @Size(max = 100, message = "{validation.email.size}")
            String email,

            @Schema(description = "schema.field.name", example = "홍길동")
            @NotBlank(message = "{validation.name.required}")
            @Size(max = 50, message = "{validation.name.size}")
            String name,

            @Schema(description = "schema.field.depart-cd", example = "DEV01")
            @Size(max = 50, message = "{validation.depart.size}")
            String departCd,     //role 필드 없음 — 등록은 항상 MEMBER, 관리자 지정은 PUT /{userId}/role

            @Schema(description = "schema.field.work-start", example = "09:00")
            @Pattern(regexp = TIME_PATTERN, message = "{validation.work-time.format}")
            String workStart,    //null 허용 → 09:00

            @Schema(description = "schema.field.work-end", example = "18:00")
            @Pattern(regexp = TIME_PATTERN, message = "{validation.work-time.format}")
            String workEnd,      //null 허용 → 18:00

            //입사일(선택, ISO yyyy-MM-dd) — 미입력 시 등록일(CURDATE). 연차 계산 기준(#11)
            @Schema(description = "schema.field.hire-date", example = "2023-03-02")
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "{validation.date.format}")
            String hireDate,

            //월 기본급(원/円, 선택) — 급여 정산(참고) 기준값. null=미입력. 0~10억 사이.
            @Schema(description = "schema.field.salary", example = "3000000")
            @Min(value = 0, message = "{validation.salary.range}")
            @Max(value = 1_000_000_000L, message = "{validation.salary.range}")
            Long baseMonthlySalary) {
    }

    //통합 최종 계약(이메일 × 스케줄 병합 — 교차 리뷰 CR3-3 확정). initialPassword 폐지.
    @Schema(description = "schema.member-create-response")
    public record MemberCreateResponse(
            long userId, String email, String name, String departCd,
            Role role, UserStatus status,                    //항상 MEMBER / PENDING
            @Schema(description = "schema.field.work-start") String workStart,
            @Schema(description = "schema.field.work-end") String workEnd,
            @Schema(description = "schema.field.mail-sent") boolean mailSent,   //false면 재발송 유도
            @Schema(description = "schema.field.invite-expires-at") LocalDateTime inviteExpiresAt) {
    }

    @Schema(description = "schema.invite-response")
    public record InviteResponse(
            long userId, String email,
            @Schema(description = "schema.field.mail-sent") boolean mailSent,
            @Schema(description = "schema.field.invite-expires-at") LocalDateTime inviteExpiresAt) {
    }

    @Schema(description = "schema.member-response")
    public record MemberResponse(
            long userId, String email, String name, String departCd,
            Role role, UserStatus status, LocalDateTime createdAt,
            @Schema(description = "schema.field.work-start") String workStart,
            @Schema(description = "schema.field.work-end") String workEnd,
            //요일별 근무 플래그(월화수목금토일, '1'=근무 — V12)
            @Schema(description = "schema.field.work-days", example = "1111100") String workDays,
            //월 기본급(원/円) — 미입력이면 null
            @Schema(description = "schema.field.salary", example = "3000000") Long baseMonthlySalary,
            //PENDING+유효 INVITE 토큰이면 그 만료시각, 아니면 null(만료/실패 → "재발송 필요" 표시)
            @Schema(description = "schema.field.invite-expires-at") LocalDateTime inviteExpiresAt) {

        public static MemberResponse from(User user, LocalDateTime inviteExpiresAt) {
            return new MemberResponse(user.userId(), user.email(), user.name(),
                    user.departCd(), user.role(), user.status(), user.createdAt(),
                    formatTime(user.defaultWorkStart()), formatTime(user.defaultWorkEnd()),
                    user.workDays(), user.baseMonthlySalary(), inviteExpiresAt);
        }
    }

    @Schema(description = "schema.member-salary-request")
    public record MemberSalaryRequest(
            //월 기본급(원/円) — null이면 미입력으로 저장(정산 계산 제외). 0~10억.
            @Schema(description = "schema.field.salary", example = "3000000")
            @Min(value = 0, message = "{validation.salary.range}")
            @Max(value = 1_000_000_000L, message = "{validation.salary.range}")
            Long baseMonthlySalary) {
    }

    @Schema(description = "schema.member-status-request")
    public record MemberStatusRequest(
            @NotNull(message = "{validation.member-status.required}") UserStatus status) { //ACTIVE|DISABLED
    }

    @Schema(description = "schema.member-role-request")
    public record MemberRoleRequest(
            @NotNull(message = "{validation.member-role.required}") Role role) { //TENANT_ADMIN|MEMBER (SYSTEM_ADMIN은 400)
    }

    @Schema(description = "schema.member-schedule-request")
    public record MemberScheduleRequest(
            @Schema(description = "schema.field.work-start", example = "09:00")
            @NotBlank(message = "{validation.work-time.required}")
            @Pattern(regexp = TIME_PATTERN, message = "{validation.work-time.format}")
            String workStart,

            @Schema(description = "schema.field.work-end", example = "18:00")
            @NotBlank(message = "{validation.work-time.required}")
            @Pattern(regexp = TIME_PATTERN, message = "{validation.work-time.format}")
            String workEnd,

            //요일별 근무 플래그(월~일). 최소 1일 근무는 서비스에서 검증
            @Schema(description = "schema.field.work-days", example = "1111100")
            @NotBlank(message = "{validation.work-days.required}")
            @Pattern(regexp = "^[01]{7}$", message = "{validation.work-days.format}")
            String workDays) {
    }

}
