package com.attendance.pro.auth;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.attendance.pro.user.Role;

/**
 * HTTP 세션에 보관하는 로그인 유저 정보.
 * tenantId는 항상 이 세션 값에서만 취득한다(요청 값 불신 — 멀티테넌시 격리 원칙).
 *
 * issuedAt은 세션 발급 시각(로그인 시점) — 비밀번호 변경(password_changed_at) 이전에 발급된
 * 세션을 SessionRevalidationInterceptor가 즉시 회수하는 기준이다. 재검증의 role 갱신 경로는
 * 원래 issuedAt을 보존한다(재검증이 세션을 "연장"하지 않게).
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
        LocalDateTime issuedAt) implements Serializable {

    /** 세션 속성 키 */
    public static final String SESSION_KEY = "LOGIN_USER";

}
