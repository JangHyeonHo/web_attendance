package com.attendance.pro.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.attendance.pro.common.ApiException;

/**
 * 비밀번호 온보딩 레이트 리밋 테스트 — RST-04(계정 3회/IP 30회 슬라이딩 윈도우, LoginRateLimiter 구성 계승).
 */
class PasswordResetRateLimiterTest {

    /** 테스트에서 시각을 임의로 진행시키는 가변 Clock */
    private static class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong(1_000_000L);

        @Override
        public long millis() {
            return millis.get();
        }

        void advance(long deltaMillis) {
            millis.addAndGet(deltaMillis);
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis());
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private void expectRateLimited(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(429);
                    assertThat(apiException.getCode()).isEqualTo("RATE_LIMITED");
                    assertThat(apiException.getMessageKey()).isEqualTo("auth.login.rate-limited");
                });
    }

    @Test
    @DisplayName("RST-04a: reset-request 계정 키 — 5분 내 3회 허용, 4회째 429(계정 실존 여부 무관)")
    void resetRequestAccountThreshold() {
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(new MutableClock());
        for (int i = 0; i < 3; i++) {
            limiter.checkResetRequest("ACME", "nobody@acme.co.kr", "10.0.0.1");
        }
        expectRateLimited(() -> limiter.checkResetRequest("ACME", "nobody@acme.co.kr", "10.0.0.1"));
        //다른 계정 키는 독립(IP 임계 30회 미만)
        assertThatCode(() -> limiter.checkResetRequest("ACME", "other@acme.co.kr", "10.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RST-04b: verify/set IP 키 — 30회 허용, 31회째 429")
    void tokenAttemptIpThreshold() {
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(new MutableClock());
        for (int i = 0; i < 30; i++) {
            limiter.checkTokenAttempt("10.0.0.9");
        }
        expectRateLimited(() -> limiter.checkTokenAttempt("10.0.0.9"));
        //다른 IP는 독립
        assertThatCode(() -> limiter.checkTokenAttempt("10.0.0.10")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("슬라이딩 윈도우: 5분 경과 후에는 다시 허용된다")
    void windowSlides() {
        MutableClock clock = new MutableClock();
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(clock);
        for (int i = 0; i < 3; i++) {
            limiter.checkResetRequest("ACME", "hong@acme.co.kr", "10.0.0.1");
        }
        expectRateLimited(() -> limiter.checkResetRequest("ACME", "hong@acme.co.kr", "10.0.0.1"));

        clock.advance(PasswordResetRateLimiter.WINDOW_MILLIS + 1);

        assertThatCode(() -> limiter.checkResetRequest("ACME", "hong@acme.co.kr", "10.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("계정 키는 테넌트+이메일 소문자 정규화 — 대소문자 변형 우회 불가")
    void accountKeyNormalized() {
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(new MutableClock());
        limiter.checkResetRequest("ACME", "Hong@acme.co.kr", "10.0.0.1");
        limiter.checkResetRequest("acme", "hong@ACME.co.kr", "10.0.0.2");
        limiter.checkResetRequest(" ACME ", "HONG@acme.co.kr", "10.0.0.3");
        expectRateLimited(() -> limiter.checkResetRequest("ACME", "hong@acme.co.kr", "10.0.0.4"));
    }

}
