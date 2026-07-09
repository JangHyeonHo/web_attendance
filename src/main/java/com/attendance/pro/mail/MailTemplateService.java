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

    public MailTemplateService(MailTemplateMapper mailTemplateMapper) {
        this.mailTemplateMapper = mailTemplateMapper;
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

    /** 발송 렌더 결과. */
    public record RenderedMail(String subject, String body) {
    }

    /**
     * 발송용 렌더. 치환 후 잔존 플레이스홀더가 있으면 발송 중단(예외 — 호출부가 mailSent=false 처리).
     */
    public RenderedMail render(TokenPurpose purpose, String lang, Map<String, String> variables) {
        MailTemplate template = mailTemplateMapper.find(purpose, lang);
        if (template == null) {
            throw templateNotFound();
        }
        String subject = substitute(template.subject(), variables);
        String body = substitute(template.body(), variables);
        String leftover = firstPlaceholder(subject + "\n" + body);
        if (leftover != null) {
            //DB 직수정 등으로 허용 외 변수가 들어온 방어 — 미치환 본문을 발송하지 않는다
            throw new IllegalStateException("unresolved mail placeholder: {" + leftover + "}");
        }
        return new RenderedMail(subject, body);
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

    private String substitute(String text, Map<String, String> variables) {
        String result = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String firstPlaceholder(String text) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
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
