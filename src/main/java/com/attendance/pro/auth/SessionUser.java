package com.attendance.pro.auth;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.attendance.pro.user.Role;

/**
 * HTTP 세션에 보관하는 로그인 유저 정보.
 * tenantId는 항상 이 세션 값에서만 취득한다(요청 값 불신 — 멀티테넌시 격리 원칙).
 *
 * passwordChangedAt은 로그인 시점의 password_changed_at 스냅샷 — 이후 DB 값과 달라지면
 * SessionRevalidationInterceptor가 세션을 즉시 회수한다(동등 비교라 앱/DB 시계 오차와 무관).
 * sessionToken은 로그인 시 발급된 단일 세션 토큰 — DB의 users.session_token과 달라지면
 * (= 다른 기기에서 새로 로그인) 마찬가지로 세션을 회수한다(단일 세션 강제).
 * 배포 주의: 직렬화 형태 변경으로 배포 시점의 기존 세션은 전원 재로그인(역직렬화 실패 = 세션 무효).
 */
public record SessionUser(
        long userId,
        long tenantId,
        String tenantCode,
        String tenantName,
        String email,
        String name,
        Role role,
        LocalDateTime passwordChangedAt,
        String sessionToken) implements Serializable {

    /** 세션 속성 키 */
    public static final String SESSION_KEY = "LOGIN_USER";

}
