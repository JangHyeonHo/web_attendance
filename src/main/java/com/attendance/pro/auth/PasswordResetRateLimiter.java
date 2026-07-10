package com.attendance.pro.auth;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.attendance.pro.common.ApiException;

/**
 * 비밀번호 온보딩 API 레이트 리밋(LoginRateLimiter 계승 — 인메모리 2단 슬라이딩 윈도우).
 * <ul>
 *   <li>reset-request: 계정 키({@code racct:테넌트코드:이메일}) 5분 3회 + IP 키 5분 30회</li>
 *   <li>verify/set: IP 키 5분 30회</li>
 *   <li>초대 재발송(TA/SA): 수신자 키({@code inv:테넌트ID:이메일}) 5분 3회 — 메일 폭탄 방지</li>
 * </ul>
 * 카운팅은 계정 실존 여부와 무관하게 동일하다(존재 오라클 방지 — 메일 폭탄/토큰 브루트포스 차단).
 * 초과 시 429 {@code RATE_LIMITED}. 수평 확장 시 세션 외부화(Redis)와 함께 이동한다.
 */
@Component
public class PasswordResetRateLimiter {

    /** 슬라이딩 윈도우(밀리초): 5분 */
    static final long WINDOW_MILLIS = 5 * 60 * 1000L;
    /** reset-request 계정 키 임계: 윈도우 내 3회 */
    static final int RESET_ACCOUNT_THRESHOLD = 3;
    /** IP 키 임계: 윈도우 내 30회 */
    static final int IP_THRESHOLD = 30;
    /** 메모리 상한(키 스프레이 방지) */
    static final int MAX_KEYS = 100_000;

    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public PasswordResetRateLimiter() {
        this(Clock.systemUTC());
    }

    PasswordResetRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** reset-request 시도 기록 + 검사(계정 키 3회/IP 30회 — 초과 429). */
    public void checkResetRequest(String tenantCode, String email, String ip) {
        long now = clock.millis();
        hit("racct:" + lower(tenantCode) + ":" + lower(email), now, RESET_ACCOUNT_THRESHOLD);
        hit("rip:" + (ip == null ? "" : ip), now, IP_THRESHOLD);
    }

    /** verify/set 시도 기록 + 검사(IP 30회 — 초과 429). */
    public void checkTokenAttempt(String ip) {
        hit("tip:" + (ip == null ? "" : ip), clock.millis(), IP_THRESHOLD);
    }

    /** 초대 재발송 기록 + 검사(수신자 키 3회 — 초과 429. 관리자 조작 실수/남용의 메일 폭탄 방지). */
    public void checkInviteResend(long tenantId, String email) {
        hit("inv:" + tenantId + ":" + lower(email), clock.millis(), RESET_ACCOUNT_THRESHOLD);
    }

    private void hit(String key, long now, int threshold) {
        evictIfNeeded(now);
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() <= now - WINDOW_MILLIS) {
                deque.pollFirst();
            }
            if (deque.size() >= threshold) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "auth.login.rate-limited");
            }
            deque.addLast(now);
        }
    }

    private void evictIfNeeded(long now) {
        if (hits.size() < MAX_KEYS) {
            return;
        }
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast() <= now - WINDOW_MILLIS;
            }
        });
        Iterator<String> it = hits.keySet().iterator();
        while (hits.size() >= MAX_KEYS && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

}
