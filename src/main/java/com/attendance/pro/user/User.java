package com.attendance.pro.user;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 회원(users 테이블).
 * passwordChangedAt은 재로그인 강제 기준(NULL=이력 없음, 기존 유저).
 * 근무 스케줄은 정기 패턴(schedule_pattern) + 상세 로타(work_schedule)에서 관리한다(스케줄 단일화 —
 * 옛 users.default_work_start/end·work_days는 폐기).
 * hireDate는 연차 자동 계산의 기산(입사일, V16 — 기존 행은 created_at 날짜로 백필).
 */
public record User(
        Long userId,
        long tenantId,
        String email,
        String passwordHash,
        LocalDateTime passwordChangedAt,
        String name,
        String departCd,
        LocalDate hireDate,
        Long baseMonthlySalary,
        Role role,
        UserStatus status,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
