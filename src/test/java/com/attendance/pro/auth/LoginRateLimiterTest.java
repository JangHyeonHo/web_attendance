package com.attendance.pro.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.attendance.pro.common.ApiException;

/**
 * 로그인 레이트 리밋 테스트(임계 도달/차단 해제/성공 초기화/윈도우 슬라이딩).
 * 케이스 ID: test-plan §1-3 LGN-08~10.
 */
class LoginRateLimiterTest {

    /** 테스트용 가변 시계 */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-07-08T00:00:00Z");

        void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock);

    private void fail(String tenantCode, String email, String ip) {
        limiter.recordFailure(tenantCode, email, ip);
    }

    @Test
    @DisplayName("LGN-08: 같은 계정 키로 5분 내 실패 5회 후 6번째 시도는 429")
    void accountThreshold() {
        for (int i = 0; i < 4; i++) {
            fail("ACME", "hong@example.com", "10.0.0.1");
            assertThatCode(() -> limiter.check("ACME", "hong@example.com", "10.0.0.1"))
                    .doesNotThrowAnyException();
        }
        fail("ACME", "hong@example.com", "10.0.0.1");  //5번째 실패 → 차단
        assertThatThrownBy(() -> limiter.check("ACME", "hong@example.com", "10.0.0.1"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(429);
                    assertThat(apiException.getCode()).isEqualTo("RATE_LIMITED");
                    assertThat(apiException.getMessageKey()).isEqualTo("auth.login.rate-limited");
                });
        //계정 키는 대소문자 무관(정규화) — 존재 오라클 방지를 위해 실존 여부와 무관하게 동일 적용
        assertThatThrownBy(() -> limiter.check("acme", "HONG@example.com", "10.0.0.9"))
                .isInstanceOf(ApiException.class);
        //다른 계정은 영향 없음
        assertThatCode(() -> limiter.check("ACME", "other@example.com", "10.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LGN-09: 같은 IP에서 계정을 바꿔가며 5분 내 실패 30회 후 시도는 429")
    void ipThreshold() {
        for (int i = 0; i < 30; i++) {
            fail("ACME", "user" + i + "@example.com", "10.0.0.7");
        }
        //계정이 바뀌어도 IP 키가 차단한다(스터핑 차단)
        assertThatThrownBy(() -> limiter.check("BETA", "fresh@example.com", "10.0.0.7"))
                .isInstanceOf(ApiException.class);
        //다른 IP는 영향 없음
        assertThatCode(() -> limiter.check("BETA", "fresh@example.com", "10.0.0.8"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LGN-08(감사): recordFailure는 차단 발동(임계 도달) 호출에만 true를 반환한다(LOGIN_BLOCKED 1회 트리거)")
    void recordFailureSignalsBlockEngagementOnce() {
        for (int i = 0; i < 4; i++) {
            //임계 미만 실패는 차단 미발동 → false
            assertThat(limiter.recordFailure("ACME", "hong@example.com", "10.0.0.1")).isFalse();
        }
        //5번째 실패에서 임계 도달 → 차단 발동 → true(감사 1회)
        assertThat(limiter.recordFailure("ACME", "hong@example.com", "10.0.0.1")).isTrue();
    }

    @Test
    @DisplayName("LGN-10: 차단 시간(계정 5분) 경과 후 재시도 허용")
    void blockExpires() {
        for (int i = 0; i < 5; i++) {
            fail("ACME", "hong@example.com", "10.0.0.1");
        }
        assertThatThrownBy(() -> limiter.check("ACME", "hong@example.com", "10.0.0.1"))
                .isInstanceOf(ApiException.class);
        clock.advanceMillis(LoginRateLimiter.ACCOUNT_BLOCK_MILLIS + 1);
        assertThatCode(() -> limiter.check("ACME", "hong@example.com", "10.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LGN-10: 성공 로그인은 계정 카운터를 초기화한다(IP 키는 유지)")
    void successResetsAccountCounter() {
        for (int i = 0; i < 4; i++) {
            fail("ACME", "hong@example.com", "10.0.0.1");
        }
        limiter.reset("ACME", "hong@example.com");
        //초기화 후 4회 더 실패해도 아직 차단되지 않는다(카운터가 0부터)
        for (int i = 0; i < 4; i++) {
            fail("ACME", "hong@example.com", "10.0.0.1");
        }
        assertThatCode(() -> limiter.check("ACME", "hong@example.com", "10.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LGN-10: 슬라이딩 윈도우 — 5분이 지난 실패는 카운트에서 빠진다")
    void slidingWindow() {
        for (int i = 0; i < 4; i++) {
            fail("ACME", "hong@example.com", "10.0.0.1");
        }
        //윈도우(5분)를 넘겨 만료시킨다
        clock.advanceMillis(LoginRateLimiter.WINDOW_MILLIS + 1);
        fail("ACME", "hong@example.com", "10.0.0.1");  //누적 5회째지만 앞 4회는 만료 → 차단 없음
        assertThatCode(() -> limiter.check("ACME", "hong@example.com", "10.0.0.1"))
                .doesNotThrowAnyException();
    }

}
