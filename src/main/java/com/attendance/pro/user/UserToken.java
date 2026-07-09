package com.attendance.pro.user;

import java.time.LocalDateTime;

/**
 * 유저 토큰(user_token 테이블) — 원문 비저장(SHA-256 해시만), 1회용.
 */
public record UserToken(
        String tokenHash,
        long tenantId,
        long userId,
        TokenPurpose purpose,
        LocalDateTime expiresAt,
        LocalDateTime usedAt,
        LocalDateTime createdAt) {
}
