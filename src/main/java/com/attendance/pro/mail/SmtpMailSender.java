package com.attendance.pro.mail;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * 운영(prod) SMTP 발송기 — spring-boot-starter-mail의 {@link JavaMailSender} 위임.
 * 본문은 HTML로 발송한다(#11 — 템플릿의 HTML 태그가 실제 메일에서 렌더). 평문 템플릿의 줄바꿈은
 * pre-wrap 래퍼로 보존해 기존 시드(평문)도 그대로 읽힌다. 본문 로그 금지(전문 로그는 dev 한정).
 */
@Component
@Profile("prod")
public class SmtpMailSender implements MailSender {

    private final JavaMailSender javaMailSender;
    private final String from;

    public SmtpMailSender(JavaMailSender javaMailSender, @Value("${app.mail.from}") String from) {
        this.javaMailSender = javaMailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            //본문이 HTML이면 그대로 발송(개행 있는 편집 가능 템플릿이 pre-wrap로 깨지지 않게, #13).
            //평문(태그 없음)이면 pre-wrap 래퍼로 줄바꿈 보존.
            String html = looksLikeHtml(body)
                    ? body
                    : "<div style=\"white-space:pre-wrap;font-family:sans-serif\">" + body + "</div>";
            helper.setText(html, true);
        } catch (MessagingException e) {
            throw new MailPreparationException("failed to build HTML mail", e);
        }
        javaMailSender.send(message);
    }

    /** 본문에 HTML 태그가 있으면 HTML로 간주(<div>, <p>, <a ...> 등). */
    private static boolean looksLikeHtml(String body) {
        return body != null && body.matches("(?s).*<[a-zA-Z][^>]*>.*");
    }

}
