package com.attendance.pro.user;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 회원(users 테이블).
 * passwordChangedAt은 재로그인 강제 기준(NULL=이력 없음, 기존 유저).
 * defaultWorkStart/End는 개인 기본 근무 스케줄(V7 [S-1]).
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
        Role role,
        UserStatus status,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
