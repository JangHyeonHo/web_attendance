package com.attendance.pro.user;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 3단계 권한.
 * 서열 비교(atLeast)는 두지 않는다 — 인가는 경로별 허용 role "화이트리스트"가 단일 소스
 * ({@code RoleInterceptor}/{@code Screen}).
 * SYSTEM_ADMIN이 TENANT_ADMIN의 상위가 아니다: 운영자의 출결·멤버 데이터 접근 차단 원칙.
 */
@Schema(description = "schema.field.role", enumAsRef = true)
public enum Role {
    MEMBER, TENANT_ADMIN, SYSTEM_ADMIN
}
