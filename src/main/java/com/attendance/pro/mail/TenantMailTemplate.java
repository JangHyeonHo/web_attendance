package com.attendance.pro.mail;

import java.time.LocalDateTime;

import com.attendance.pro.user.TokenPurpose;

/**
 * 테넌트별 메일 템플릿 오버라이드(tenant_mail_template 테이블).
 * 행이 없으면 전역 {@link MailTemplate}이 기본값으로 쓰인다.
 */
public record TenantMailTemplate(
        long tenantId,
        TokenPurpose purpose,
        String lang,
        String subject,
        String body,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
