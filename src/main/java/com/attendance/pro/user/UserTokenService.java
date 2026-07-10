package com.attendance.pro.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.common.ApiException;

/**
 * 유저 토큰(초대/재설정) 발급·검증·무효화 서비스.
 *
 * 토큰 설계 정본(email-onboarding §3):
 * <ul>
 *   <li>생성: SecureRandom 32바이트 → Base64URL(43자, 패딩 없음) — 256bit 엔트로피</li>
 *   <li>저장: SHA-256 hex 해시만(원문 비저장) — DB 덤프 유출 시에도 링크 재구성 불가</li>
 *   <li>검증: 부존재/만료/사용 완료 전부 동일 404(사유 비노출 — 열거 방지)</li>
 *   <li>무효화: 재발급 시 같은 (user, purpose) DELETE — 살아있는 링크는 최신 1개 이하</li>
 * </ul>
 */
@Service
public class UserTokenService {

    /** 토큰 원문 바이트 수(256bit) */
    private static final int TOKEN_BYTES = 32;

    private final UserTokenMapper userTokenMapper;
    private final SecureRandom random = new SecureRandom();

    public UserTokenService(UserTokenMapper userTokenMapper) {
        this.userTokenMapper = userTokenMapper;
    }

    /** 발급 결과 — 원문은 메일 링크 조립에 1회 쓰고 폐기한다(어디에도 저장하지 않음). */
    public record IssuedToken(String token, LocalDateTime expiresAt) {
    }

    /**
     * 토큰 발급. 같은 (user, purpose)의 기존 토큰은 삭제(구 링크 즉시 무효 — 재발송 겸용).
     * 만료 30일 경과분 청소를 부수 실행한다.
     */
    @Transactional
    public IssuedToken issue(long tenantId, long userId, TokenPurpose purpose) {
        userTokenMapper.deleteExpired();
        userTokenMapper.deleteByUserAndPurpose(tenantId, userId, purpose);
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expiresAt = LocalDateTime.now().plus(purpose.ttl());
        userTokenMapper.insert(tenantId, sha256Hex(token), userId, purpose, expiresAt);
        return new IssuedToken(token, expiresAt);
    }

    /**
     * 토큰 원문 검증. 부존재/만료/사용 완료는 전부 동일 404 {@code TOKEN_INVALID}.
     */
    public UserToken verify(String rawToken) {
        UserToken token = userTokenMapper.findByHash(sha256Hex(rawToken));
        if (token == null || token.usedAt() != null
                || token.expiresAt().isBefore(LocalDateTime.now())) {
            throw invalidToken();
        }
        return token;
    }

    /**
     * 사용 처리(1회용 확정 + 성공 감사 흔적) — 설정 트랜잭션 안에서 호출한다.
     * UPDATE 조건에 {@code used_at IS NULL}이 있어 반환 0 = 이미 다른 요청이 선점(동시 사용 방지의 원자 지점).
     */
    public int markUsed(long tenantId, String tokenHash) {
        return userTokenMapper.markUsed(tenantId, tokenHash);
    }

    /** 유저의 유효 토큰 전멸(정지·삭제·설정 성공 시). */
    public void invalidateAll(long tenantId, long userId) {
        userTokenMapper.deleteByUser(tenantId, userId);
    }

    /** 유효 INVITE 토큰의 유저별 만료 시각(멤버 목록 조립용). */
    public Map<Long, LocalDateTime> findActiveInviteExpiries(long tenantId) {
        return userTokenMapper.findInviteExpiries(tenantId).stream()
                .collect(Collectors.toMap(UserTokenMapper.InviteExpiry::userId,
                        UserTokenMapper.InviteExpiry::expiresAt));
    }

    /** 부존재=만료=사용 완료 통일 404 — 사유 비노출. */
    public static ApiException invalidToken() {
        return ApiException.notFound("TOKEN_INVALID", "auth.token.invalid");
    }

    /** SHA-256 hex — attendance_check와 동일한 "원문 비저장" 원칙. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

}
