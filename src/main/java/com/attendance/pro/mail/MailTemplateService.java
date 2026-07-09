package com.attendance.pro.mail;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.mail.MailTemplateDtos.MailTemplatePreviewResponse;
import com.attendance.pro.mail.MailTemplateDtos.MailTemplateResponse;
import com.attendance.pro.mail.MailTemplateDtos.TenantMailTemplateResponse;
import com.attendance.pro.user.TokenPurpose;

/**
 * 메일 템플릿 로드·검증·치환 렌더 서비스(SYSTEM_ADMIN 관리 + 발송 렌더 공용).
 *
 * 치환 누락 검증 2층(email-onboarding §4.4):
 * <ol>
 *   <li>저장/미리보기 시 — 허용 외 {@code {...}} 400, 본문 {@code {actionUrl}} 필수</li>
 *   <li>발송 시(방어) — 치환 후 잔존 플레이스홀더 검출 시 발송 중단 + ERROR 로그(DB 직수정 대비)</li>
 * </ol>
 */
@Service
public class MailTemplateService {

    /** 허용 변수 — INVITE 전량, RESET은 inviterName 제외 4종 */
    private static final Set<String> INVITE_VARS =
            Set.of("memberName", "tenantName", "actionUrl", "expiresAt", "inviterName");
    private static final Set<String> RESET_VARS =
            Set.of("memberName", "tenantName", "actionUrl", "expiresAt");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z]+)\\}");
    private static final Set<String> LANGS = Set.of("KOR", "ENG", "JPN");
    private static final DateTimeFormatter EXPIRES_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MailTemplateMapper mailTemplateMapper;
    private final TenantMailTemplateMapper tenantMailTemplateMapper;

    public MailTemplateService(MailTemplateMapper mailTemplateMapper,
            TenantMailTemplateMapper tenantMailTemplateMapper) {
        this.mailTemplateMapper = mailTemplateMapper;
        this.tenantMailTemplateMapper = tenantMailTemplateMapper;
    }

    public List<MailTemplateResponse> list() {
        return mailTemplateMapper.findAll().stream().map(MailTemplateResponse::from).toList();
    }

    /**
     * 템플릿 수정(생성/삭제 없음 — 행 집합은 시드 6행 고정). 미지 purpose/lang은 404.
     */
    @Transactional
    public MailTemplateResponse update(String purpose, String lang, String subject, String body) {
        TokenPurpose resolved = resolvePurpose(purpose);
        String resolvedLang = resolveLang(lang);
        validate(resolved, subject, body);
        int updated = mailTemplateMapper.update(resolved, resolvedLang, subject, body);
        if (updated == 0) {
            throw templateNotFound();
        }
        return MailTemplateResponse.from(mailTemplateMapper.find(resolved, resolvedLang));
    }

    /**
     * 미리보기 — 저장하지 않고 샘플 값으로 치환한 결과를 돌려준다(저장과 같은 검증을 먼저 통과 가능).
     */
    public MailTemplatePreviewResponse preview(String purpose, String lang, String subject, String body) {
        TokenPurpose resolved = resolvePurpose(purpose);
        resolveLang(lang);
        validate(resolved, subject, body);
        Map<String, String> samples = sampleVariables(resolved);
        return new MailTemplatePreviewResponse(substitute(subject, samples), substitute(body, samples));
    }

    // ---------------------------------------------------------
    // 회사(테넌트)별 오버라이드 — 기본 템플릿(전역)은 그대로 두고 자기 회사 문구만 교체
    // ---------------------------------------------------------

    /**
     * 테넌트의 유효 템플릿 목록 — 전역 6행을 기준으로 오버라이드가 있으면 그 내용으로 대체해 돌려준다.
     */
    public List<TenantMailTemplateResponse> listEffective(long tenantId) {
        Map<String, TenantMailTemplate> overrides = new LinkedHashMap<>();
        for (TenantMailTemplate override : tenantMailTemplateMapper.findByTenant(tenantId)) {
            overrides.put(override.purpose() + ":" + override.lang(), override);
        }
        return mailTemplateMapper.findAll().stream().map(base -> {
            TenantMailTemplate override = overrides.get(base.purpose() + ":" + base.lang());
            if (override == null) {
                return new TenantMailTemplateResponse(base.purpose(), base.lang(),
                        base.subject(), base.body(), false, base.updatedAt());
            }
            return new TenantMailTemplateResponse(override.purpose(), override.lang(),
                    override.subject(), override.body(), true, override.updatedAt());
        }).toList();
    }

    /**
     * 회사별 오버라이드 저장(create-or-replace). 전역과 동일 검증 규칙, 대상 행은 전역 6행 집합으로 한정.
     */
    @Transactional
    public TenantMailTemplateResponse updateOverride(long tenantId, String purpose, String lang,
            String subject, String body) {
        TokenPurpose resolved = resolvePurpose(purpose);
        String resolvedLang = resolveLang(lang);
        if (mailTemplateMapper.find(resolved, resolvedLang) == null) {
            throw templateNotFound(); //전역 기본이 없는 조합은 오버라이드도 불가
        }
        validate(resolved, subject, body);
        tenantMailTemplateMapper.upsert(tenantId, resolved, resolvedLang, subject, body);
        TenantMailTemplate saved = tenantMailTemplateMapper.find(tenantId, resolved, resolvedLang);
        return new TenantMailTemplateResponse(saved.purpose(), saved.lang(),
                saved.subject(), saved.body(), true, saved.updatedAt());
    }

    /**
     * 기본값으로 되돌리기 — 오버라이드 행 삭제. 오버라이드가 없으면 404.
     */
    @Transactional
    public void revertOverride(long tenantId, String purpose, String lang) {
        TokenPurpose resolved = resolvePurpose(purpose);
        String resolvedLang = resolveLang(lang);
        if (tenantMailTemplateMapper.delete(tenantId, resolved, resolvedLang) == 0) {
            throw templateNotFound();
        }
    }

    /** 발송 렌더 결과. */
    public record RenderedMail(String subject, String body) {
    }

    /**
     * 발송용 렌더 — 해석 순서: 테넌트 오버라이드 → 전역 기본.
     * 치환 후 잔존 플레이스홀더가 있으면 발송 중단(예외 — 호출부가 mailSent=false 처리).
     */
    public RenderedMail render(long tenantId, TokenPurpose purpose, String lang, Map<String, String> variables) {
        String subjectTemplate;
        String bodyTemplate;
        TenantMailTemplate override = tenantMailTemplateMapper.find(tenantId, purpose, lang);
        if (override != null) {
            subjectTemplate = override.subject();
            bodyTemplate = override.body();
        } else {
            MailTemplate template = mailTemplateMapper.find(purpose, lang);
            if (template == null) {
                throw templateNotFound();
            }
            subjectTemplate = template.subject();
            bodyTemplate = template.body();
        }
        if (!bodyTemplate.contains("{actionUrl}")) {
            //DB 직수정으로 링크 변수가 지워진 방어 — 링크 없는 초대/재설정 메일은 기능 불능이므로 발송 중단
            throw new IllegalStateException("mail template body lost {actionUrl}: " + purpose + "/" + lang);
        }
        //잔존 검출은 치환 "전" 템플릿을 스캔한다 — 치환 결과 스캔이면 변수 값에 든 중괄호
        //(예: 이름 "{길동}")가 오검출돼 그 대상만 영구 발송 불능이 된다(리뷰 P3-2)
        String unknown = firstUnknownPlaceholder(subjectTemplate + "\n" + bodyTemplate, variables);
        if (unknown != null) {
            //DB 직수정 등으로 허용 외 변수가 들어온 방어 — 미치환 본문을 발송하지 않는다
            throw new IllegalStateException("unresolved mail placeholder: {" + unknown + "}");
        }
        return new RenderedMail(substitute(subjectTemplate, variables), substitute(bodyTemplate, variables));
    }

    /** 미리보기 샘플 값(email-onboarding §6.1). */
    private Map<String, String> sampleVariables(TokenPurpose purpose) {
        Map<String, String> samples = new LinkedHashMap<>();
        samples.put("memberName", "홍길동");
        samples.put("tenantName", "에이크미(주)");
        samples.put("actionUrl", "https://acme.webatt.example/?token=SAMPLE");
        samples.put("expiresAt", LocalDateTime.now().plus(purpose.ttl()).format(EXPIRES_FORMAT));
        if (purpose == TokenPurpose.INVITE) {
            samples.put("inviterName", "김관리");
        }
        return samples;
    }

    /** {expiresAt} 렌더 포맷(서버 시각대 yyyy-MM-dd HH:mm). */
    public static String formatExpiresAt(LocalDateTime expiresAt) {
        return expiresAt.format(EXPIRES_FORMAT);
    }

    private void validate(TokenPurpose purpose, String subject, String body) {
        Set<String> allowed = purpose == TokenPurpose.INVITE ? INVITE_VARS : RESET_VARS;
        Matcher matcher = PLACEHOLDER.matcher(subject + "\n" + body);
        while (matcher.find()) {
            String var = matcher.group(1);
            if (!allowed.contains(var)) {
                throw ApiException.badRequest("MAIL_TEMPLATE_UNKNOWN_VAR", "mail-template.unknown-var", var);
            }
        }
        if (!body.contains("{actionUrl}")) {
            //링크 없는 메일은 기능 불능
            throw ApiException.badRequest("MAIL_TEMPLATE_ACTION_URL_REQUIRED", "mail-template.action-url.required");
        }
    }

    /**
     * 단일 패스 치환 — 순차 replace와 달리 변수 "값"에 든 중괄호/변수 표기가 재해석되지 않는다.
     * 맵에 없는 플레이스홀더는 원문 유지(잔존 검출은 render의 사전 템플릿 스캔이 담당).
     */
    private String substitute(String text, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** 치환 전 템플릿에서 변수 맵에 없는 첫 플레이스홀더 이름(없으면 null). */
    private String firstUnknownPlaceholder(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            if (!variables.containsKey(matcher.group(1))) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private TokenPurpose resolvePurpose(String purpose) {
        if (purpose == null) {
            throw templateNotFound();
        }
        try {
            return TokenPurpose.valueOf(purpose.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw templateNotFound();
        }
    }

    private String resolveLang(String lang) {
        String normalized = lang == null ? "" : lang.trim().toUpperCase(Locale.ROOT);
        if (!LANGS.contains(normalized)) {
            throw templateNotFound();
        }
        return normalized;
    }

    private static ApiException templateNotFound() {
        return ApiException.notFound("MAIL_TEMPLATE_NOT_FOUND", "mail-template.not-found");
    }

}
