package com.attendance.pro.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 개발/테스트용 페이크 발송기 — 발송 대신 INFO 로그로 전문을 출력한다.
 * dev 전용이므로 actionUrl 포함 전문을 남긴다(로컬 수동 테스트·E2E가 로그에서 링크를 취득).
 * 발송 내용을 노출하는 조회 API는 두지 않는다.
 */
@Component
@Profile("!prod")
public class LoggingMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[MAIL] to={}\nsubject={}\n{}", to, subject, body);
    }

}
