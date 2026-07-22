package com.attendance.pro.common;

import java.util.List;

/**
 * 페이지 번호 방식 목록 응답(#9 리스트 상한 정책).
 * 무한 증가 리스트(멤버 목록·휴가 신청 내역 등)는 전건 반환 대신 이 형태로 페이지 조회한다.
 *
 * @param items      현재 페이지 행
 * @param page       현재 페이지(1부터)
 * @param size       페이지 크기
 * @param totalCount 전체 건수
 * @param totalPages 전체 페이지 수(최소 1 — 빈 목록도 1페이지로 표기)
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalCount, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalCount) {
        int totalPages = (int) Math.max(1, (totalCount + size - 1) / size);
        return new PageResponse<>(items, page, size, totalCount, totalPages);
    }

    /** 페이지 파라미터 정규화 — 1 미만은 1로, size는 1~{maxSize}로 클램프(과대 요청 방어). */
    public static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    public static int normalizeSize(Integer size, int defaultSize, int maxSize) {
        if (size == null || size < 1) {
            return defaultSize;
        }
        return Math.min(size, maxSize);
    }
}
