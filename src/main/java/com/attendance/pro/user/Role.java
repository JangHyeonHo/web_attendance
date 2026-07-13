package com.attendance.pro.user;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 권한. 회사 내 3계층 + 운영사 1계층.
 * 서열 비교(atLeast)는 두지 않는다 — 인가는 경로별 허용 role "화이트리스트"가 단일 소스
 * ({@code RoleInterceptor}/{@code Screen}).
 * SYSTEM_ADMIN이 TENANT_ADMIN의 상위가 아니다: 운영자의 출결·멤버 데이터 접근 차단 원칙.
 *
 * 회사 내: MEMBER < HR_ADMIN(인사관리자) < TENANT_ADMIN(총관리자).
 * 인사관리자는 사람·근태·(후속)휴가를 다루되, 관리자 임명·회사 메일 설정은 총관리자 전용(직권 분산 — Phase 6).
 */
@Schema(description = "schema.field.role", enumAsRef = true)
public enum Role {
    MEMBER, HR_ADMIN, TENANT_ADMIN, SYSTEM_ADMIN
}
