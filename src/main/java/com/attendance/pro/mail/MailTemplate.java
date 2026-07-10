package com.attendance.pro.mail;

import java.time.LocalDateTime;

import com.attendance.pro.user.TokenPurpose;

/**
 * 메일 템플릿(mail_template 테이블) — 행 집합은 시드 6행(purpose×lang) 고정.
 */
public record MailTemplate(
        TokenPurpose purpose,
        String lang,
        String subject,
        String body,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
