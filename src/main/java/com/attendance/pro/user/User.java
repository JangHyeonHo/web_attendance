package com.attendance.pro.user;

import java.time.LocalDateTime;

/**
 * 회원(users 테이블).
 */
public record User(
        Long userId,
        long tenantId,
        String email,
        String passwordHash,
        String name,
        String departCd,
        Role role,
        UserStatus status,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
