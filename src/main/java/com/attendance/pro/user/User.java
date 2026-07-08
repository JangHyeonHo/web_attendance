package com.attendance.pro.user;

import java.time.LocalDateTime;

/**
 * 회원(users 테이블).
 */
public record User(
        Long userId,
        String email,
        String passwordHash,
        String name,
        String departCd,
        boolean admin,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
