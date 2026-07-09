package com.attendance.pro.tenant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 테넌트 상태. DB는 VARCHAR(10) — MyBatis 기본 이름 매핑.
 * SUSPENDED 테넌트는 전 멤버 로그인이 차단된다.
 */
@Schema(description = "schema.field.tenant-status", enumAsRef = true)
public enum TenantStatus {
    ACTIVE, SUSPENDED
}
