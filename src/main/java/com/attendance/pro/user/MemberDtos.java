package com.attendance.pro.user;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 멤버 관리 API(/api/v1/tenant/members) 요청/응답 DTO 모음.
 */
public final class MemberDtos {

    /** 영문 대소문자/숫자/특수문자 중 3종 이상 조합 8~30자 (구 UserDtos에서 이관) */
    public static final String PASSWORD_PATTERN =
            "^((?=.*[\\d])(?=.*[a-z])(?=.*[A-Z])|(?=.*[a-z])(?=.*[A-Z])(?=.*[^\\w\\d\\s])|(?=.*[\\d])(?=.*[A-Z])(?=.*[^\\w\\d\\s])|(?=.*[\\d])(?=.*[a-z])(?=.*[^\\w\\d\\s])).{8,30}$";

    private MemberDtos() {
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
            String departCd) {    //role 필드 없음 — 등록은 항상 MEMBER, 관리자 지정은 PUT /{userId}/role
    }

    @Schema(description = "schema.member-create-response")
    public record MemberCreateResponse(
            long userId, String email, String name, String departCd,
            Role role, UserStatus status,
            @Schema(description = "schema.field.initial-password") String initialPassword) {
    }

    @Schema(description = "schema.member-response")
    public record MemberResponse(
            long userId, String email, String name, String departCd,
            Role role, UserStatus status, LocalDateTime createdAt) {

        public static MemberResponse from(User user) {
            return new MemberResponse(user.userId(), user.email(), user.name(),
                    user.departCd(), user.role(), user.status(), user.createdAt());
        }
    }

    @Schema(description = "schema.member-status-request")
    public record MemberStatusRequest(
            @NotNull(message = "{validation.member-status.required}") UserStatus status) { //ACTIVE|DISABLED
    }

    @Schema(description = "schema.member-role-request")
    public record MemberRoleRequest(
            @NotNull(message = "{validation.member-role.required}") Role role) { //TENANT_ADMIN|MEMBER (SYSTEM_ADMIN은 400)
    }

}
