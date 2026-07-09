package com.attendance.pro.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.mail.MailTemplateService.RenderedMail;
import com.attendance.pro.user.TokenPurpose;

/**
 * 메일 템플릿 검증·치환 렌더 테스트 — TPL-02(변수 검증)·TPL-03(렌더/잔존 방어).
 */
@ExtendWith(MockitoExtension.class)
class MailTemplateServiceTest {

    @Mock
    private MailTemplateMapper mailTemplateMapper;
    @Mock
    private TenantMailTemplateMapper tenantMailTemplateMapper;

    private MailTemplateService service() {
        return new MailTemplateService(mailTemplateMapper, tenantMailTemplateMapper);
    }

    private static MailTemplate template(TokenPurpose purpose, String subject, String body) {
        return new MailTemplate(purpose, "KOR", subject, body, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("TPL-02a: 미지 변수 {foo} 저장은 400 MAIL_TEMPLATE_UNKNOWN_VAR(변수명 포함)")
    void unknownVariableRejected() {
        assertThatThrownBy(() -> service().update("INVITE", "KOR", "제목 {foo}", "본문 {actionUrl}"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(400);
                    assertThat(apiException.getCode()).isEqualTo("MAIL_TEMPLATE_UNKNOWN_VAR");
                    assertThat(apiException.getArgs()).containsExactly("foo");
                });
        verify(mailTemplateMapper, never()).update(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("TPL-02b: 본문 {actionUrl} 누락은 400 MAIL_TEMPLATE_ACTION_URL_REQUIRED")
    void actionUrlRequired() {
        assertThatThrownBy(() -> service().update("INVITE", "KOR", "제목", "링크 없는 본문"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo("MAIL_TEMPLATE_ACTION_URL_REQUIRED"));
    }

    @Test
    @DisplayName("RESET 템플릿에 {inviterName}은 허용 외 변수(4종만 허용)")
    void resetDisallowsInviterName() {
        assertThatThrownBy(() -> service().update("RESET", "KOR", "제목", "{inviterName} {actionUrl}"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getCode()).isEqualTo("MAIL_TEMPLATE_UNKNOWN_VAR");
                    assertThat(apiException.getArgs()).containsExactly("inviterName");
                });
    }

    @Test
    @DisplayName("미지 purpose/lang은 404 MAIL_TEMPLATE_NOT_FOUND")
    void unknownPurposeOrLang() {
        assertThatThrownBy(() -> service().update("NOPE", "KOR", "제목", "{actionUrl}"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MAIL_TEMPLATE_NOT_FOUND"));
        assertThatThrownBy(() -> service().update("INVITE", "FRA", "제목", "{actionUrl}"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MAIL_TEMPLATE_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 수정은 검증 통과 후 갱신 행을 돌려준다")
    void updateSucceeds() {
        when(mailTemplateMapper.update(TokenPurpose.INVITE, "KOR", "새 제목 {tenantName}", "본문 {actionUrl}"))
                .thenReturn(1);
        when(mailTemplateMapper.find(TokenPurpose.INVITE, "KOR"))
                .thenReturn(template(TokenPurpose.INVITE, "새 제목 {tenantName}", "본문 {actionUrl}"));

        var response = service().update("INVITE", "KOR", "새 제목 {tenantName}", "본문 {actionUrl}");

        assertThat(response.subject()).isEqualTo("새 제목 {tenantName}");
    }

    @Test
    @DisplayName("TPL-03a: 발송 렌더는 5변수를 전부 치환한다")
    void renderSubstitutesAll() {
        when(mailTemplateMapper.find(TokenPurpose.INVITE, "KOR")).thenReturn(template(TokenPurpose.INVITE,
                "[{tenantName}] {memberName}님 초대",
                "{inviterName}이 초대. {actionUrl} 만료 {expiresAt}"));

        RenderedMail mail = service().render(10L, TokenPurpose.INVITE, "KOR", Map.of(
                "memberName", "홍길동", "tenantName", "에이크미(주)",
                "actionUrl", "http://localhost:5173/?token=T",
                "expiresAt", "2026-07-12 09:00", "inviterName", "김관리"));

        assertThat(mail.subject()).isEqualTo("[에이크미(주)] 홍길동님 초대");
        assertThat(mail.body())
                .contains("김관리이 초대", "http://localhost:5173/?token=T", "2026-07-12 09:00")
                .doesNotContain("{");
    }

    @Test
    @DisplayName("TPL-03c: DB 직수정으로 본문에서 {actionUrl}이 사라져도 발송 중단(링크 없는 메일 방지)")
    void renderAbortsWhenActionUrlStripped() {
        when(tenantMailTemplateMapper.find(10L, TokenPurpose.INVITE, "KOR")).thenReturn(null);
        when(mailTemplateMapper.find(TokenPurpose.INVITE, "KOR")).thenReturn(
                template(TokenPurpose.INVITE, "제목", "링크 변수가 지워진 본문"));

        assertThatThrownBy(() -> service().render(10L, TokenPurpose.INVITE, "KOR", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("actionUrl");
    }

    @Test
    @DisplayName("TPL-04a: 회사 오버라이드가 있으면 렌더는 오버라이드를 쓴다(전역 미조회)")
    void renderPrefersTenantOverride() {
        when(tenantMailTemplateMapper.find(10L, TokenPurpose.INVITE, "KOR")).thenReturn(
                new TenantMailTemplate(10L, TokenPurpose.INVITE, "KOR",
                        "[회사설정] {tenantName}", "우리 회사 문구 {actionUrl}",
                        LocalDateTime.now(), LocalDateTime.now()));

        RenderedMail mail = service().render(10L, TokenPurpose.INVITE, "KOR", Map.of(
                "tenantName", "에이크미(주)", "actionUrl", "http://x/?token=T"));

        assertThat(mail.subject()).isEqualTo("[회사설정] 에이크미(주)");
        assertThat(mail.body()).contains("우리 회사 문구");
        verify(mailTemplateMapper, never()).find(any(), anyString());
    }

    @Test
    @DisplayName("TPL-04b: 오버라이드가 없으면 전역 기본으로 폴백")
    void renderFallsBackToGlobal() {
        when(tenantMailTemplateMapper.find(10L, TokenPurpose.RESET, "KOR")).thenReturn(null);
        when(mailTemplateMapper.find(TokenPurpose.RESET, "KOR")).thenReturn(
                template(TokenPurpose.RESET, "기본 제목 {tenantName}", "{actionUrl}"));

        RenderedMail mail = service().render(10L, TokenPurpose.RESET, "KOR", Map.of(
                "tenantName", "에이크미(주)", "actionUrl", "http://x/?token=T"));

        assertThat(mail.subject()).isEqualTo("기본 제목 에이크미(주)");
    }

    @Test
    @DisplayName("TPL-04c: 유효 목록은 전역 6행 기준 — 오버라이드 행만 overridden=true·내용 대체")
    void listEffectiveMergesOverrides() {
        when(mailTemplateMapper.findAll()).thenReturn(java.util.List.of(
                template(TokenPurpose.INVITE, "기본 초대", "{actionUrl}"),
                template(TokenPurpose.RESET, "기본 재설정", "{actionUrl}")));
        when(tenantMailTemplateMapper.findByTenant(10L)).thenReturn(java.util.List.of(
                new TenantMailTemplate(10L, TokenPurpose.INVITE, "KOR",
                        "회사 초대", "회사 본문 {actionUrl}", LocalDateTime.now(), LocalDateTime.now())));

        var list = service().listEffective(10L);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).overridden()).isTrue();
        assertThat(list.get(0).subject()).isEqualTo("회사 초대");
        assertThat(list.get(1).overridden()).isFalse();
        assertThat(list.get(1).subject()).isEqualTo("기본 재설정");
    }

    @Test
    @DisplayName("TPL-04d: 되돌리기 — 오버라이드가 없으면 404")
    void revertWithoutOverrideIs404() {
        when(tenantMailTemplateMapper.delete(10L, TokenPurpose.INVITE, "KOR")).thenReturn(0);

        assertThatThrownBy(() -> service().revertOverride(10L, "INVITE", "KOR"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    @Test
    @DisplayName("TPL-04e: 오버라이드 저장도 전역과 동일 검증(미지 변수 400) + 전역에 없는 조합은 404")
    void overrideValidation() {
        when(mailTemplateMapper.find(TokenPurpose.INVITE, "KOR"))
                .thenReturn(template(TokenPurpose.INVITE, "기본", "{actionUrl}"));
        assertThatThrownBy(() -> service().updateOverride(10L, "INVITE", "KOR", "제목 {foo}", "{actionUrl}"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MAIL_TEMPLATE_UNKNOWN_VAR"));

        when(mailTemplateMapper.find(TokenPurpose.RESET, "ENG")).thenReturn(null);
        assertThatThrownBy(() -> service().updateOverride(10L, "RESET", "ENG", "제목", "{actionUrl}"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    @Test
    @DisplayName("TPL-03b: 치환 후 잔존 플레이스홀더가 있으면 발송 중단(예외)")
    void renderAbortsOnLeftoverPlaceholder() {
        //DB 직수정으로 허용 외 변수가 들어간 상황
        when(mailTemplateMapper.find(TokenPurpose.RESET, "ENG")).thenReturn(template(TokenPurpose.RESET,
                "reset {unknownVar}", "{actionUrl}"));

        assertThatThrownBy(() -> service().render(10L, TokenPurpose.RESET, "ENG",
                Map.of("actionUrl", "http://x/?token=T")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknownVar");
    }

    @Test
    @DisplayName("미리보기는 저장 없이 샘플 값 치환 결과를 돌려준다")
    void previewUsesSampleValues() {
        var preview = service().preview("INVITE", "KOR",
                "[{tenantName}] 초대", "{memberName}: {actionUrl} / {inviterName}");

        assertThat(preview.subject()).isEqualTo("[에이크미(주)] 초대");
        assertThat(preview.body()).contains("홍길동", "김관리").doesNotContain("{");
        verify(mailTemplateMapper, never()).update(any(), anyString(), anyString(), anyString());
    }

}
