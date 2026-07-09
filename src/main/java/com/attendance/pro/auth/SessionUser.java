package com.attendance.pro.auth;

import java.io.Serializable;

import com.attendance.pro.user.Role;

/**
 * HTTP 세션에 보관하는 로그인 유저 정보.
 * tenantId는 항상 이 세션 값에서만 취득한다(요청 값 불신 — 멀티테넌시 격리 원칙).
 */
public record SessionUser(
        long userId,
        long tenantId,
        String tenantCode,
        String tenantName,
        String email,
        String name,
        Role role) implements Serializable {

    /** 세션 속성 키 */
    public static final String SESSION_KEY = "LOGIN_USER";

}
