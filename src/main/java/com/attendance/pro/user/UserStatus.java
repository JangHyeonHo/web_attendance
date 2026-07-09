package com.attendance.pro.user;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 계정 상태.
 * PENDING은 셀프가입(승인제) 대비 예약값 — 멤버 관리 API로는 만들 수 없다.
 */
@Schema(description = "schema.field.member-status", enumAsRef = true)
public enum UserStatus {
    PENDING, ACTIVE, DISABLED
}
