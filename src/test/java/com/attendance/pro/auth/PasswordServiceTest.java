package com.attendance.pro.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.attendance.pro.auth.AuthDtos.TokenVerifyResponse;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.tenant.TenantStatus;
import com.attendance.pro.user.MemberInviteService;
import com.attendance.pro.user.Role;
import com.attendance.pro.user.TokenPurpose;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;
import com.attendance.pro.user.UserStatus;
import com.attendance.pro.user.UserToken;
import com.attendance.pro.user.UserTokenService;

/**
 * 공개 비밀번호 설정/재설정 서비스 테스트 — INV-03/04·RST-01/05·TOK-02·DEL 연계.
 */
@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final long TENANT_ID = 10L;
    private static final long USER_ID = 5L;
    private static final String RAW_TOKEN = "raw-token";
    private static final String TOKEN_HASH = UserTokenService.sha256Hex(RAW_TOKEN);

    @Mock
    private UserTokenService userTokenService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private MemberInviteService memberInviteService;
    @Mock
    private com.attendance.pro.billing.BillingService billingService;

    private PasswordService service() {
        return new PasswordService(userTokenService, userMapper, tenantMapper, memberInviteService,
                billingService);
    }

    private static UserToken token(TokenPurpose purpose) {
        return new UserToken(TOKEN_HASH, TENANT_ID, USER_ID, purpose,
                LocalDateTime.now().plusHours(1), null, LocalDateTime.now());
    }

    private static User user(UserStatus status) {
        return new User(USER_ID, TENANT_ID, "hong@acme.co.kr", "old-hash", null, "홍길동", null,
                LocalTime.of(9, 0), LocalTime.of(18, 0), "1111100", null,
                Role.MEMBER, status, false, LocalDateTime.now(), LocalDateTime.now());
    }

    private static Tenant tenant(TenantStatus status) {
        return new Tenant(TENANT_ID, "ACME", "에이크미(주)", "KR", status, LocalDateTime.now());
    }

    private void expectTokenInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(404);
                    assertThat(apiException.getCode()).isEqualTo("TOKEN_INVALID");
                });
    }

    @Nested
    @DisplayName("verify — W010 표시 정보")
    class Verify {

        @Test
        @DisplayName("INV-03(U): 유효 INVITE 토큰 verify는 이름/마스킹 이메일/회사명/만료를 돌려준다")
        void verifyReturnsDisplayInfo() {
            when(userTokenService.verify(RAW_TOKEN)).thenReturn(token(TokenPurpose.INVITE));
            when(userMapper.findById(TENANT_ID, USER_ID)).thenReturn(user(UserStatus.PENDING));
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant(TenantStatus.ACTIVE));

            TokenVerifyResponse response = service().verify(RAW_TOKEN);

            assertThat(response.purpose()).isEqualTo(TokenPurpose.INVITE);
            assertThat(response.name()).isEqualTo("홍길동");
            assertThat(response.emailMasked()).isEqualTo("ho**@acme.co.kr");
            assertThat(response.tenantName()).isEqualTo("에이크미(주)");
            assertThat(response.expiresAt()).isNotNull();
        }

        @Test
        @DisplayName("TOK-02: 삭제(조회 null)·정지 유저의 발급 완료 토큰 verify는 404(무효화 이중 방어)")
        void verifyRejectsDeletedOrDisabledUser() {
            when(userTokenService.verify(RAW_TOKEN)).thenReturn(token(TokenPurpose.RESET));
            //삭제 유저: findById(deleted 제외)가 null
            when(userMapper.findById(TENANT_ID, USER_ID)).thenReturn(null);
            expectTokenInvalid(() -> service().verify(RAW_TOKEN));
            //정지 유저: RESET은 ACTIVE 기대 — DISABLED면 404
            when(userMapper.findById(TENANT_ID, USER_ID)).thenReturn(user(UserStatus.DISABLED));
            expectTokenInvalid(() -> service().verify(RAW_TOKEN));
        }

        @Test
        @DisplayName("용도-상태 불일치(INVITE인데 ACTIVE)는 통일 404")
        void verifyRejectsStatusMismatch() {
            when(userTokenService.verify(RAW_TOKEN)).thenReturn(token(TokenPurpose.INVITE));
            when(userMapper.findById(TENANT_ID, USER_ID)).thenReturn(user(UserStatus.ACTIVE));
            expectTokenInvalid(() -> service().verify(RAW_TOKEN));
        }

        @Test
        @DisplayName("정지(SUSPENDED) 테넌트의 토큰 verify는 통일 404 — 정지 회사 우회 차단(리뷰 P3-3)")
        void verifyRejectsSuspendedTenant() {
            when(userTokenService.verify(RAW_TOKEN)).thenReturn(token(TokenPurpose.RESET));
            when(userMapper.findById(TENANT_ID, USER_ID)).thenReturn(user(UserStatus.ACTIVE));
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant(TenantStatus.SUSPENDED));
            expectTokenInvalid(() -> service().verify(RAW_TOKEN));
        }
    }

    @Nested
    @DisplayName("set — 비밀번호 설정")
    class Set {

        @Test
        @DisplayName("INV-03(U): INVITE set은 hash 교체(BCrypt) + PENDING→ACTIVE + markUsed + 잔여 토큰 전멸")
        void inviteSetActivates() {
            when(userTokenService.verify(RAW_TOKEN)).thenReturn(token(TokenPurpose.INVITE));
            when(userMapper.findById(TENANT_ID, USER_ID)).thenReturn(user(UserStatus.PENDING));
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant(TenantStatus.ACTIVE));
            when(userTokenService.markUsed(TENANT_ID, TOKEN_HASH)).thenReturn(1);

            service().set(RAW_TOKEN, "NewPass1!word");

            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            verify(userMapper).updatePassword(eq(TENANT_ID), eq(USER_ID), hashCaptor.capture());
            assertThat(new BCryptPasswordEncoder().matches("NewPass1!word", hashCaptor.getValue())).isTrue();
            verify(userMapper).updateStatus(TENANT_ID, USER_ID, UserStatus.ACTIVE);
            verify(userTokenService).markUsed(TENANT_ID, TOKEN_HASH);
            verify(userTokenService).invalidateAll(TENANT_ID, USER_ID);
        }

        @Test
        @DisplayName("RST set은 status 불변(updateStatus 미호출) — hash 교체만")
        void resetSetKeepsStatus() {
            when(userTokenService.verify(RAW_TOKEN)).thenReturn(token(TokenPurpose.RESET));
            when(userMapper.findById(TENANT_ID, USER_ID)).thenReturn(user(UserStatus.ACTIVE));
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant(TenantStatus.ACTIVE));
            when(userTokenService.markUsed(TENANT_ID, TOKEN_HASH)).thenReturn(1);

            service().set(RAW_TOKEN, "NewPass1!word");

            verify(userMapper).updatePassword(eq(TENANT_ID), eq(USER_ID), anyString());
            verify(userMapper, never()).updateStatus(anyLong(), anyLong(), eq(UserStatus.ACTIVE));
            verify(userTokenService).markUsed(TENANT_ID, TOKEN_HASH);
        }

        @Test
        @DisplayName("동시 2요청 중 늦은 쪽(markUsed=0)은 404 — 변경 없음(1회용의 원자 보장)")
        void concurrentSecondSetRejected() {
            when(userTokenService.verify(RAW_TOKEN)).thenReturn(token(TokenPurpose.RESET));
            when(userMapper.findById(TENANT_ID, USER_ID)).thenReturn(user(UserStatus.ACTIVE));
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant(TenantStatus.ACTIVE));
            when(userTokenService.markUsed(TENANT_ID, TOKEN_HASH)).thenReturn(0);

            expectTokenInvalid(() -> service().set(RAW_TOKEN, "NewPass1!word"));
            verify(userMapper, never()).updatePassword(anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("정지(SUSPENDED) 테넌트의 토큰 set은 404 — 변경 없음(리뷰 P3-3)")
        void suspendedTenantSetRejected() {
            when(userTokenService.verify(RAW_TOKEN)).thenReturn(token(TokenPurpose.RESET));
            when(userMapper.findById(TENANT_ID, USER_ID)).thenReturn(user(UserStatus.ACTIVE));
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant(TenantStatus.SUSPENDED));

            expectTokenInvalid(() -> service().set(RAW_TOKEN, "NewPass1!word"));
            verify(userMapper, never()).updatePassword(anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("INV-04(U): 무효 토큰 set은 404 단일 코드 — 변경 없음")
        void invalidTokenSetRejected() {
            when(userTokenService.verify(RAW_TOKEN)).thenThrow(UserTokenService.invalidToken());
            expectTokenInvalid(() -> service().set(RAW_TOKEN, "NewPass1!word"));
            verify(userMapper, never()).updatePassword(anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("용도-상태 불일치 set(RESET인데 PENDING)은 404 — 변경 없음")
        void statusMismatchSetRejected() {
            when(userTokenService.verify(RAW_TOKEN)).thenReturn(token(TokenPurpose.RESET));
            when(userMapper.findById(TENANT_ID, USER_ID)).thenReturn(user(UserStatus.PENDING));
            expectTokenInvalid(() -> service().set(RAW_TOKEN, "NewPass1!word"));
            verify(userMapper, never()).updatePassword(anyLong(), anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("reset-request — 존재 비노출(RST-01/05)")
    class ResetRequest {

        @Test
        @DisplayName("RST-05(U): ACTIVE 계정만 RESET 발송 — 비동기 위임(응답 시간 오라클 차단, 리뷰 P3-1)")
        void activeAccountSends() {
            when(tenantMapper.findByCode("ACME")).thenReturn(tenant(TenantStatus.ACTIVE));
            when(userMapper.findByEmail(TENANT_ID, "hong@acme.co.kr")).thenReturn(user(UserStatus.ACTIVE));

            service().requestReset("acme", "hong@acme.co.kr");

            verify(memberInviteService).sendResetAsync(TENANT_ID, USER_ID, "hong@acme.co.kr", "홍길동");
        }

        @Test
        @DisplayName("RST-01(U): 부존재/PENDING/DISABLED/테넌트 부존재·정지 — 전부 조용히 무시(예외·발송 없음)")
        void silentForNonEligible() {
            PasswordService service = service();
            //테넌트 부존재
            when(tenantMapper.findByCode("NOPE")).thenReturn(null);
            service.requestReset("NOPE", "hong@acme.co.kr");
            //테넌트 정지
            when(tenantMapper.findByCode("SLEEP")).thenReturn(
                    new Tenant(30L, "SLEEP", "슬립(주)", "KR", TenantStatus.SUSPENDED, LocalDateTime.now()));
            service.requestReset("SLEEP", "hong@acme.co.kr");
            //계정 부존재
            when(tenantMapper.findByCode("ACME")).thenReturn(tenant(TenantStatus.ACTIVE));
            when(userMapper.findByEmail(TENANT_ID, "nobody@acme.co.kr")).thenReturn(null);
            service.requestReset("ACME", "nobody@acme.co.kr");
            //PENDING / DISABLED
            when(userMapper.findByEmail(TENANT_ID, "hong@acme.co.kr"))
                    .thenReturn(user(UserStatus.PENDING))
                    .thenReturn(user(UserStatus.DISABLED));
            service.requestReset("ACME", "hong@acme.co.kr");
            service.requestReset("ACME", "hong@acme.co.kr");

            verify(memberInviteService, never()).sendResetAsync(anyLong(), anyLong(), anyString(), anyString());
        }
    }

}
