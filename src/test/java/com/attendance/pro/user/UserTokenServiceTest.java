package com.attendance.pro.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.user.UserTokenService.IssuedToken;

/**
 * 유저 토큰 발급·검증 테스트 — TOK-01(원문 비저장)·RST-05(TTL)·INV-04(통일 404).
 */
@ExtendWith(MockitoExtension.class)
class UserTokenServiceTest {

    private static final long TENANT_ID = 10L;
    private static final long USER_ID = 5L;

    @Mock
    private UserTokenMapper userTokenMapper;

    private UserTokenService service() {
        return new UserTokenService(userTokenMapper);
    }

    private static UserToken token(String hash, TokenPurpose purpose,
            LocalDateTime expiresAt, LocalDateTime usedAt) {
        return new UserToken(hash, TENANT_ID, USER_ID, purpose, expiresAt, usedAt, LocalDateTime.now());
    }

    private void expectTokenInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(404);
                    assertThat(apiException.getCode()).isEqualTo("TOKEN_INVALID");
                    assertThat(apiException.getMessageKey()).isEqualTo("auth.token.invalid");
                });
    }

    @Test
    @DisplayName("TOK-01: 발급 토큰은 원문 43자 Base64URL, DB 저장값은 SHA-256(원문) hex")
    void issueStoresOnlyHash() throws Exception {
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);

        IssuedToken issued = service().issue(TENANT_ID, USER_ID, TokenPurpose.INVITE);

        verify(userTokenMapper).insert(eq(TENANT_ID), hashCaptor.capture(), eq(USER_ID),
                eq(TokenPurpose.INVITE), any(LocalDateTime.class));
        assertThat(issued.token()).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String expectedHash = HexFormat.of()
                .formatHex(md.digest(issued.token().getBytes(StandardCharsets.UTF_8)));
        assertThat(hashCaptor.getValue()).isEqualTo(expectedHash).isNotEqualTo(issued.token());
    }

    @Test
    @DisplayName("RST-05: INVITE는 72h, RESET은 30m TTL이 부여된다")
    void ttlPerPurpose() {
        LocalDateTime before = LocalDateTime.now();

        IssuedToken invite = service().issue(TENANT_ID, USER_ID, TokenPurpose.INVITE);
        IssuedToken reset = service().issue(TENANT_ID, USER_ID, TokenPurpose.RESET);

        assertThat(invite.expiresAt()).isBetween(before.plusHours(72), before.plusHours(72).plusMinutes(1));
        assertThat(reset.expiresAt()).isBetween(before.plusMinutes(30), before.plusMinutes(31));
    }

    @Test
    @DisplayName("발급 시 같은 (user, purpose)의 기존 토큰 삭제 + 만료분 청소 — 살아있는 링크는 1개 이하")
    void issueInvalidatesPrevious() {
        service().issue(TENANT_ID, USER_ID, TokenPurpose.INVITE);

        verify(userTokenMapper).deleteExpired();
        verify(userTokenMapper).deleteByUserAndPurpose(TENANT_ID, USER_ID, TokenPurpose.INVITE);
    }

    @Test
    @DisplayName("verify: 유효 토큰은 행을 돌려준다(해시 조회)")
    void verifyValidToken() {
        String raw = "raw-token";
        String hash = UserTokenService.sha256Hex(raw);
        when(userTokenMapper.findByHash(hash))
                .thenReturn(token(hash, TokenPurpose.INVITE, LocalDateTime.now().plusHours(1), null));

        UserToken result = service().verify(raw);

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.userId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("INV-04: 부존재/만료/사용 완료 전부 동일 404 TOKEN_INVALID(사유 비노출)")
    void verifyRejectsUniform404() {
        UserTokenService service = service();
        //부존재
        when(userTokenMapper.findByHash(UserTokenService.sha256Hex("absent"))).thenReturn(null);
        expectTokenInvalid(() -> service.verify("absent"));
        //만료
        when(userTokenMapper.findByHash(UserTokenService.sha256Hex("expired"))).thenReturn(
                token("h1", TokenPurpose.RESET, LocalDateTime.now().minusMinutes(1), null));
        expectTokenInvalid(() -> service.verify("expired"));
        //사용 완료
        when(userTokenMapper.findByHash(UserTokenService.sha256Hex("used"))).thenReturn(
                token("h2", TokenPurpose.INVITE, LocalDateTime.now().plusHours(1), LocalDateTime.now()));
        expectTokenInvalid(() -> service.verify("used"));
    }

    @Test
    @DisplayName("TOK-01(S 대응): DB의 token_hash 값을 그대로 토큰으로 제출하면 404(해시의 해시 불일치)")
    void hashItselfIsNotAValidToken() {
        String raw = "raw-token";
        String hash = UserTokenService.sha256Hex(raw);
        //해시 원문을 토큰으로 제출 → 해시의 해시로 조회되어 불일치
        when(userTokenMapper.findByHash(UserTokenService.sha256Hex(hash))).thenReturn(null);
        expectTokenInvalid(() -> service().verify(hash));
    }

}
