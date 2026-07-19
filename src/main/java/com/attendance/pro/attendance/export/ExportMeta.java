package com.attendance.pro.attendance.export;

/** 내보내기 문맥 — 제목/파일 헤더에 쓰는 부가 정보(근태 데이터 외). */
public record ExportMeta(String tenantName, String memberName, int year, int month) {
}
