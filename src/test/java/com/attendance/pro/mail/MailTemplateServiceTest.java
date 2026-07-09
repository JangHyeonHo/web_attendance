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

    private MailTemplateService service() {
        return new MailTemplateService(mailTemplateMapper);
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

        RenderedMail mail = service().render(TokenPurpose.INVITE, "KOR", Map.of(
                "memberName", "홍길동", "tenantName", "에이크미(주)",
                "actionUrl", "http://localhost:5173/?token=T",
                "expiresAt", "2026-07-12 09:00", "inviterName", "김관리"));

        assertThat(mail.subject()).isEqualTo("[에이크미(주)] 홍길동님 초대");
        assertThat(mail.body())
                .contains("김관리이 초대", "http://localhost:5173/?token=T", "2026-07-12 09:00")
                .doesNotContain("{");
    }

    @Test
    @DisplayName("TPL-03b: 치환 후 잔존 플레이스홀더가 있으면 발송 중단(예외)")
    void renderAbortsOnLeftoverPlaceholder() {
        //DB 직수정으로 허용 외 변수가 들어간 상황
        when(mailTemplateMapper.find(TokenPurpose.RESET, "ENG")).thenReturn(template(TokenPurpose.RESET,
                "reset {unknownVar}", "{actionUrl}"));

        assertThatThrownBy(() -> service().render(TokenPurpose.RESET, "ENG",
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
