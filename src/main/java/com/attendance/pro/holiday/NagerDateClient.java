package com.attendance.pro.holiday;

import java.util.List;

/**
 * Nager.Date 공휴일 취득 클라이언트 — 인터페이스로 두어 서비스 단위 테스트는 Mockito 목,
 * 스모크/E2E는 base-url 프로퍼티로 로컬 스텁 치환(단위 레벨에서 실 외부 API를 절대 호출하지 않는다).
 */
public interface NagerDateClient {

    /**
     * {@code GET /api/v3/PublicHolidays/{year}/{countryCode}} — 원시 목록 반환.
     * IO 예외·5xx는 1회 재시도 후, 4xx는 즉시 502 {@code HOLIDAY_SYNC_UPSTREAM}으로 귀결된다.
     */
    List<NagerHoliday> fetch(int year, String countryCode);

}
