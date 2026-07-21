package com.attendance.pro.attendance.export;

import java.time.LocalDate;

/**
 * 내보내기 문맥 — 문서 머리(날짜·회사·부서·이름), 언어(기본 폰트 선택), 도장(결재)란.
 * @param lang         KOR/ENG/JPN — 기본 폰트 결정(한:맑은 고딕, 일:Meiryo UI)
 * @param issueDate    작성일(문서 상단 '날짜')
 * @param stampArea    결재(도장)란 포함 여부(테넌트 설정)
 * @param sealApproved 그 달이 마감 승인됨 → 결재란에 도장을 실제로 찍는다(승인 전엔 빈 칸)
 * @param sealImage    회사 도장 이미지(bytes). null이면 검은 원으로 대체
 * @param sealMime     도장 이미지 MIME(image/png|image/jpeg)
 * @param sealSize     도장 표시 크기(SMALL|MEDIUM|LARGE)
 */
public record ExportMeta(String tenantName, String memberName, String department, int year, int month,
        String lang, LocalDate issueDate, boolean stampArea,
        boolean sealApproved, byte[] sealImage, String sealMime, String sealSize) {
}
