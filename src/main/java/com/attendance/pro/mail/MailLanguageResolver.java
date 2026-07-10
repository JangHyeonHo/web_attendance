package com.attendance.pro.mail;

import org.springframework.stereotype.Component;

/**
 * 메일 언어 해석 — 입력은 <b>tenant.country</b>(V7 승격 후의 정본 — CR3-1).
 * KR→KOR, JP→JPN, 그 외=ENG(방어 — country는 NOT NULL DEFAULT 'KR' + 생성 검증이라 실측 도달 불가).
 */
@Component
public class MailLanguageResolver {

    public String resolve(String country) {
        if (country == null) {
            return "ENG";
        }
        return switch (country.trim().toUpperCase(java.util.Locale.ROOT)) {
        case "KR" -> "KOR";
        case "JP" -> "JPN";
        default -> "ENG";
        };
    }

}
