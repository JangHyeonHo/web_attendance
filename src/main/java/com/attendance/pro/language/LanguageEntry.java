package com.attendance.pro.language;

/**
 * 언어 마스터(language_master 테이블) 엔트리.
 */
public record LanguageEntry(
        long languageId,
        String windowId,
        String langKey,
        String lang,
        String langValue) {
}
