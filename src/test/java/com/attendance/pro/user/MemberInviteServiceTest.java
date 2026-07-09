package com.attendance.pro.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.mail.MailLanguageResolver;
import com.attendance.pro.mail.MailSender;
import com.attendance.pro.mail.MailTemplateService;
import com.attendance.pro.mail.MailTemplateService.RenderedMail;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.tenant.TenantStatus;
import com.attendance.pro.user.MemberInviteService.InviteOutcome;
import com.attendance.pro.user.UserTokenService.IssuedToken;

/**
 * 초대·재설정 발송 오케스트레이션 테스트 — TPL-04(U, 언어=tenant.country)·링크 조립·발송 실패 삼킴.
 */
@ExtendWith(MockitoExtension.class)
class MemberInviteServiceTest {

    private static final long TENANT_ID = 10L;
    private static final long USER_ID = 5L;

    @Mock
    private UserTokenService userTokenService;
    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private MailTemplateService mailTemplateService;
    @Mock
    private MailSender mailSender;

    private MemberInviteService service(String baseDomain) {
        return new MemberInviteService(userTokenService, tenantMapper, mailTemplateService,
                new MailLanguageResolver(), mailSender, baseDomain, "http://localhost:5173");
    }

    private static Tenant tenant(String country) {
        return new Tenant(TENANT_ID, "ACME", "에이크미(주)", country, TenantStatus.ACTIVE, LocalDateTime.now());
    }

    private void stubToken(TokenPurpose purpose) {
        when(userTokenService.issue(TENANT_ID, USER_ID, purpose))
                .thenReturn(new IssuedToken("TOKEN43", LocalDateTime.of(2026, 7, 12, 9, 0)));
    }

    @Test
    @DisplayName("TPL-04(U): 초대 메일 언어는 tenant.country가 결정(KR→KOR, JP→JPN)")
    void mailLanguageFollowsTenantCountry() {
        stubToken(TokenPurpose.INVITE);
        when(mailTemplateService.render(eq(TokenPurpose.INVITE), anyString(), anyMap()))
                .thenReturn(new RenderedMail("제목", "본문"));
        //KR → KOR
        when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
        service(null).sendInvite(TENANT_ID, USER_ID, "hong@acme.co.kr", "홍길동", "김관리");
        verify(mailTemplateService).render(eq(TokenPurpose.INVITE), eq("KOR"), anyMap());
        //JP → JPN
        when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("JP"));
        service(null).sendInvite(TENANT_ID, USER_ID, "hong@acme.co.kr", "홍길동", "김관리");
        verify(mailTemplateService).render(eq(TokenPurpose.INVITE), eq("JPN"), anyMap());
    }

    @Test
    @DisplayName("변수 조립: memberName/tenantName/actionUrl/expiresAt/inviterName + 루트 링크 계약")
    void variablesAndRootLink() {
        stubToken(TokenPurpose.INVITE);
        when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        when(mailTemplateService.render(eq(TokenPurpose.INVITE), eq("KOR"), varsCaptor.capture()))
                .thenReturn(new RenderedMail("제목", "본문"));

        InviteOutcome outcome = service(null).sendInvite(TENANT_ID, USER_ID, "hong@acme.co.kr", "홍길동", "김관리");

        Map<String, String> vars = varsCaptor.getValue();
        assertThat(vars.get("memberName")).isEqualTo("홍길동");
        assertThat(vars.get("tenantName")).isEqualTo("에이크미(주)");
        assertThat(vars.get("actionUrl")).isEqualTo("http://localhost:5173/?token=TOKEN43");
        assertThat(vars.get("inviterName")).isEqualTo("김관리");
        assertThat(vars.get("expiresAt")).isEqualTo("2026-07-12 09:00");
        assertThat(outcome.mailSent()).isTrue();
        assertThat(outcome.expiresAt()).isEqualTo(LocalDateTime.of(2026, 7, 12, 9, 0));
        verify(mailSender).send("hong@acme.co.kr", "제목", "본문");
    }

    @Test
    @DisplayName("서브도메인 활성 시 링크는 https://{code 소문자}.{base-domain}/?token=... (요청 Host 불사용)")
    void subdomainLink() {
        stubToken(TokenPurpose.INVITE);
        when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        when(mailTemplateService.render(eq(TokenPurpose.INVITE), eq("KOR"), varsCaptor.capture()))
                .thenReturn(new RenderedMail("제목", "본문"));

        service("webatt.example").sendInvite(TENANT_ID, USER_ID, "hong@acme.co.kr", "홍길동", "김관리");

        assertThat(varsCaptor.getValue().get("actionUrl"))
                .isEqualTo("https://acme.webatt.example/?token=TOKEN43");
    }

    @Test
    @DisplayName("RESET은 inviterName 없이 4변수만 조립된다")
    void resetVariablesExcludeInviter() {
        stubToken(TokenPurpose.RESET);
        when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> varsCaptor = ArgumentCaptor.forClass(Map.class);
        when(mailTemplateService.render(eq(TokenPurpose.RESET), eq("KOR"), varsCaptor.capture()))
                .thenReturn(new RenderedMail("제목", "본문"));

        service(null).sendReset(TENANT_ID, USER_ID, "hong@acme.co.kr", "홍길동");

        assertThat(varsCaptor.getValue()).doesNotContainKey("inviterName");
    }

    @Test
    @DisplayName("INV-06(연계): 발송 예외는 삼키고 mailSent=false — 토큰은 이미 발급되어 유효(재발송 수습)")
    void mailFailureSwallowed() {
        stubToken(TokenPurpose.INVITE);
        when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
        when(mailTemplateService.render(eq(TokenPurpose.INVITE), anyString(), anyMap()))
                .thenReturn(new RenderedMail("제목", "본문"));
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(anyString(), anyString(), anyString());

        InviteOutcome outcome = service(null).sendInvite(TENANT_ID, USER_ID, "hong@acme.co.kr", "홍길동", "김관리");

        assertThat(outcome.mailSent()).isFalse();
        assertThat(outcome.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("토큰 발급 실패도 삼킨다(mailSent=false, expiresAt=null) — 생성 응답이 500이 되지 않게")
    void tokenIssueFailureSwallowed() {
        when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
        when(userTokenService.issue(TENANT_ID, USER_ID, TokenPurpose.INVITE))
                .thenThrow(new RuntimeException("DB down"));

        InviteOutcome outcome = service(null).sendInvite(TENANT_ID, USER_ID, "hong@acme.co.kr", "홍길동", "김관리");

        assertThat(outcome.mailSent()).isFalse();
        assertThat(outcome.expiresAt()).isNull();
        verify(mailSender, org.mockito.Mockito.never()).send(any(), any(), any());
    }

}
