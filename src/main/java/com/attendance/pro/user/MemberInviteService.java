package com.attendance.pro.user;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.attendance.pro.mail.MailLanguageResolver;
import com.attendance.pro.mail.MailSender;
import com.attendance.pro.mail.MailTemplateService;
import com.attendance.pro.mail.MailTemplateService.RenderedMail;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.user.UserTokenService.IssuedToken;

/**
 * 초대·재설정 발송 오케스트레이션 — 토큰 발급(UserTokenService) → 링크 조립 → 언어 해석 → 렌더 → MailSender.
 * 등록 트랜잭션 밖에서 호출한다(발송 실패해도 멤버·토큰은 유효 — 재발송이 수습 경로).
 *
 * 링크 조립은 요청 Host를 쓰지 않는다(Host 헤더 주입 피싱 차단 — 서버 구성값만):
 * 서브도메인 활성(app.tenant.base-domain)이면 https://{code 소문자}.{base-domain}/?token=...,
 * 그 외는 {app.mail.link-base-url}/?token=... 이 유일한 프론트 진입 계약이다.
 */
@Service
public class MemberInviteService {

    private static final Logger log = LoggerFactory.getLogger(MemberInviteService.class);

    private final UserTokenService userTokenService;
    private final TenantMapper tenantMapper;
    private final MailTemplateService mailTemplateService;
    private final MailLanguageResolver mailLanguageResolver;
    private final MailSender mailSender;
    private final String baseDomain;
    private final String linkBaseUrl;

    public MemberInviteService(UserTokenService userTokenService,
            TenantMapper tenantMapper,
            MailTemplateService mailTemplateService,
            MailLanguageResolver mailLanguageResolver,
            MailSender mailSender,
            @Value("${app.tenant.base-domain:}") String baseDomain,
            @Value("${app.mail.link-base-url:http://localhost:5173}") String linkBaseUrl) {
        this.userTokenService = userTokenService;
        this.tenantMapper = tenantMapper;
        this.mailTemplateService = mailTemplateService;
        this.mailLanguageResolver = mailLanguageResolver;
        this.mailSender = mailSender;
        this.baseDomain = baseDomain == null ? "" : baseDomain.trim();
        this.linkBaseUrl = linkBaseUrl;
    }

    /**
     * 발송 결과 — 실패해도 예외를 올리지 않는다(호출부는 mailSent 플래그로 응답).
     * expiresAt은 발급된 토큰의 만료 시각(토큰 발급 자체가 실패하면 null).
     */
    public record InviteOutcome(boolean mailSent, LocalDateTime expiresAt) {
    }

    /**
     * 초대(INVITE) 발송. 기존 INVITE 토큰은 발급 단계에서 삭제된다(구 링크 즉시 무효 — 재발송 겸용).
     */
    public InviteOutcome sendInvite(long tenantId, long userId, String email, String memberName,
            String inviterName) {
        return send(tenantId, userId, email, memberName, inviterName, TokenPurpose.INVITE);
    }

    /**
     * 재설정(RESET) 발송 — 초대와 같은 메커니즘·다른 본문(inviterName 없음).
     */
    public InviteOutcome sendReset(long tenantId, long userId, String email, String memberName) {
        return send(tenantId, userId, email, memberName, null, TokenPurpose.RESET);
    }

    /**
     * 재설정 발송의 비동기 판 — 공개 재설정 요청(202 통일 응답) 전용.
     * 동기 발송이면 계정이 실존할 때만 SMTP 왕복이 응답 시간에 실려 존재 오라클이 된다(리뷰 P3-1).
     * 실패는 여기서 로그만(응답은 이미 떠났다 — 오류를 되돌릴 곳이 없음).
     */
    @Async
    public void sendResetAsync(long tenantId, long userId, String email, String memberName) {
        InviteOutcome outcome = sendReset(tenantId, userId, email, memberName);
        if (!outcome.mailSent()) {
            log.error("password reset mail send failed: tenantId={}, userId={}", tenantId, userId);
        }
    }

    private InviteOutcome send(long tenantId, long userId, String email, String memberName,
            String inviterName, TokenPurpose purpose) {
        IssuedToken token;
        Tenant tenant;
        try {
            tenant = tenantMapper.findById(tenantId);
            if (tenant == null) {
                return new InviteOutcome(false, null);
            }
            token = userTokenService.issue(tenantId, userId, purpose);
        } catch (RuntimeException e) {
            log.error("token issue failed: tenantId={}, userId={}, purpose={}", tenantId, userId, purpose, e);
            return new InviteOutcome(false, null);
        }
        try {
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("memberName", memberName);
            variables.put("tenantName", tenant.name());
            variables.put("actionUrl", buildActionUrl(tenant.tenantCode(), token.token()));
            variables.put("expiresAt", MailTemplateService.formatExpiresAt(token.expiresAt()));
            if (purpose == TokenPurpose.INVITE) {
                variables.put("inviterName", inviterName == null ? "" : inviterName);
            }
            String lang = mailLanguageResolver.resolve(tenant.country());
            //해석 순서: 회사(테넌트) 오버라이드 → 전역 기본 템플릿
            RenderedMail mail = mailTemplateService.render(tenantId, purpose, lang, variables);
            mailSender.send(email, mail.subject(), mail.body());
            return new InviteOutcome(true, token.expiresAt());
        } catch (RuntimeException e) {
            //발송 실패는 삼킨다 — 멤버·토큰은 유효, 재발송이 수습 경로(mailSent=false 응답)
            log.error("mail send failed: tenantId={}, userId={}, purpose={}", tenantId, userId, purpose, e);
            return new InviteOutcome(false, token.expiresAt());
        }
    }

    private String buildActionUrl(String tenantCode, String token) {
        if (!baseDomain.isEmpty()) {
            return "https://" + tenantCode.toLowerCase(Locale.ROOT) + "." + baseDomain + "/?token=" + token;
        }
        return linkBaseUrl + "/?token=" + token;
    }

}
