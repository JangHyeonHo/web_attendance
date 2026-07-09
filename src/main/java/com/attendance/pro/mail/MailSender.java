package com.attendance.pro.mail;

/**
 * 텍스트 메일 발송 단일 메소드 인터페이스.
 * 스프링 {@code JavaMailSender}를 직접 노출하지 않아 페이크(LoggingMailSender) 치환점이 된다.
 */
public interface MailSender {

    void send(String to, String subject, String body);

}
