package com.attendance.pro.attendance.export;

import java.time.LocalDate;

/**
 * 내보내기 문맥 — 문서 머리(날짜·회사·이름), 언어(기본 폰트 선택), 도장(결재)란 표시 여부.
 * @param lang       KOR/ENG/JPN — 기본 폰트 결정(한:맑은 고딕, 일:Meiryo UI)
 * @param issueDate  작성일(문서 상단 '날짜')
 * @param stampArea  결재(도장)란 포함 여부(테넌트 설정)
 */
public record ExportMeta(String tenantName, String memberName, int year, int month,
        String lang, LocalDate issueDate, boolean stampArea) {
}
