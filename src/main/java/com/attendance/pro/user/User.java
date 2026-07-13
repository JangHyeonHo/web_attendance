package com.attendance.pro.user;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 회원(users 테이블).
 * passwordChangedAt은 재로그인 강제 기준(NULL=이력 없음, 기존 유저).
 * defaultWorkStart/End는 개인 기본 근무 스케줄(V7 [S-1]), workDays는 요일별 근무 플래그(V12).
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
        LocalTime defaultWorkStart,
        LocalTime defaultWorkEnd,
        String workDays,
        LocalDate hireDate,
        Role role,
        UserStatus status,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
