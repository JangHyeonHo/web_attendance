package com.attendance.pro.user;

import java.time.Duration;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 유저 토큰 용도.
 * TTL은 앱이 부여한다 — 초대는 수신자 부재 허용(72h), 재설정은 탈취 창 최소화(30m).
 */
@Schema(description = "schema.token-purpose", enumAsRef = true)
public enum TokenPurpose {

    /** 멤버/관리자 초대(비밀번호 최초 설정) */
    INVITE(Duration.ofHours(72)),
    /** 비밀번호 재설정 */
    RESET(Duration.ofMinutes(30));

    private final Duration ttl;

    TokenPurpose(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }

}
