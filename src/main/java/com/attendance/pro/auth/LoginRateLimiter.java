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
 * 로그인 레이트 리밋(인메모리, 단일 인스턴스 전제).
 * 계정 키 + IP 키 2단 슬라이딩 윈도우.
 * <ul>
 *   <li>계정 키({@code acct:테넌트코드:이메일} — 소문자): 5분 내 실패 5회 → 5분 차단</li>
 *   <li>IP 키({@code ip:주소}): 5분 내 실패 30회 → 15분 차단</li>
 * </ul>
 * 카운팅은 자격 증명 검증 <b>이전에</b> 적용되고 계정 실존 여부와 무관하게 동일하다(존재 오라클 방지).
 * 차단 시 429 {@code RATE_LIMITED}(Retry-After 헤더 없음).
 * 수평 확장 시 세션 외부화(Redis)와 함께 이 카운터도 이동한다.
 */
@Component
public class LoginRateLimiter {

    /** 슬라이딩 윈도우(밀리초): 5분 */
    static final long WINDOW_MILLIS = 5 * 60 * 1000L;
    /** 계정 키 임계: 윈도우 내 실패 5회 */
    static final int ACCOUNT_THRESHOLD = 5;
    /** IP 키 임계: 윈도우 내 실패 30회 */
    static final int IP_THRESHOLD = 30;
    /** 계정 차단 시간: 5분 */
    static final long ACCOUNT_BLOCK_MILLIS = 5 * 60 * 1000L;
    /** IP 차단 시간: 15분 */
    static final long IP_BLOCK_MILLIS = 15 * 60 * 1000L;
    /** 메모리 상한(키 스프레이 방지) */
    static final int MAX_KEYS = 100_000;

    private final Clock clock;
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 로그인 시도 전 검사. 차단 중이면 429를 던진다.
     */
    public void check(String tenantCode, String email, String ip) {
        long now = clock.millis();
        if (isBlocked(accountKey(tenantCode, email), now) || isBlocked(ipKey(ip), now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "auth.login.rate-limited");
        }
    }

    /**
     * 인증 실패 기록. 임계 도달 시 차단을 건다.
     *
     * @return 이번 호출로 계정 또는 IP 차단이 <b>새로 발동</b>했으면 true(감사 1회 기록 트리거).
     *         이미 차단 중인 반복 시도는 {@link #check}에서 걸러져 이 메소드에 도달하지 않으므로,
     *         차단 발동은 윈도우당 한 번만 true가 된다(감사 폭주 방지).
     */
    public boolean recordFailure(String tenantCode, String email, String ip) {
        long now = clock.millis();
        boolean accountBlocked = record(accountKey(tenantCode, email), now, ACCOUNT_THRESHOLD, ACCOUNT_BLOCK_MILLIS);
        boolean ipBlocked = record(ipKey(ip), now, IP_THRESHOLD, IP_BLOCK_MILLIS);
        return accountBlocked || ipBlocked;
    }

    /**
     * 인증 성공 시 해당 계정 키의 카운터를 초기화한다(IP 키는 유지).
     */
    public void reset(String tenantCode, String email) {
        String key = accountKey(tenantCode, email);
        failures.remove(key);
        blockedUntil.remove(key);
    }

    private boolean isBlocked(String key, long now) {
        Long until = blockedUntil.get(key);
        if (until == null) {
            return false;
        }
        if (until <= now) {
            blockedUntil.remove(key);
            return false;
        }
        return true;
    }

    /** @return 이번 기록으로 임계에 도달해 차단이 걸렸으면 true. */
    private boolean record(String key, long now, int threshold, long blockMillis) {
        evictIfNeeded();
        Deque<Long> deque = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            //윈도우 밖의 만료된 실패 기록 제거(슬라이딩)
            while (!deque.isEmpty() && deque.peekFirst() <= now - WINDOW_MILLIS) {
                deque.pollFirst();
            }
            deque.addLast(now);
            if (deque.size() >= threshold) {
                blockedUntil.put(key, now + blockMillis);
                deque.clear();
                return true;
            }
        }
        return false;
    }

    /**
     * 키 수 상한 도달 시 만료 항목을 정리하고, 그래도 넘치면 임의(가장 오래된 순회 순) 키부터 제거한다.
     */
    private void evictIfNeeded() {
        if (failures.size() < MAX_KEYS) {
            return;
        }
        long now = clock.millis();
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast() <= now - WINDOW_MILLIS;
            }
        });
        Iterator<String> it = failures.keySet().iterator();
        while (failures.size() >= MAX_KEYS && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    private String accountKey(String tenantCode, String email) {
        return "acct:" + lower(tenantCode) + ":" + lower(email);
    }

    private String ipKey(String ip) {
        return "ip:" + (ip == null ? "" : ip);
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

}
