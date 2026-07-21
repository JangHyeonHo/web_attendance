package com.attendance.pro.attendance;

import com.attendance.pro.tenant.ProfileCountry;

/**
 * 국가별 급여 정산(참고) 정책 — 순수 상수 enum(DB/세션 의존 없음, 조립기·BreakPolicy와 동일 단위 테스트 대상).
 *
 * 통상시급 = 월 기본급 ÷ divisor. 가산율은 통상시급에 곱하는 "추가분"(0.5 = +50%).
 * 야간 시간대는 저녁 22:00부터 다음날 아침까지 — 저녁 시작(1320분=22:00)은 양국 공통, 아침 종료만 다르다
 * (한국 06:00, 일본 05:00). 분(minute-of-day) 단위로 표현해 야근(자정 넘김) 구간과 정확히 겹친다.
 *
 * 근거:
 *  - 🇰🇷 근로기준법 §56: 연장 +50%, 야간(22:00~06:00) +50%, 휴일(8h 이내) +50%. 통상시급 ÷209h(주휴 포함).
 *  - 🇯🇵 労働基準法 §37: 시간외 +25%, 심야(22:00~05:00) +25%, 휴일 +35%. 시급 ÷월평균소정근로시간.
 * divisor·휴일가산은 회사 실태(연간휴일 등)에 따라 달라질 수 있어 "참고" 기본값이며, 실지급은 별도 급여시스템.
 */
public enum PayPolicy {

    /** 대한민국 — 근로기준법 */
    KR(209, 0.5, 0.5, 0.5, 6 * 60),

    /** 일본 — 労働基準法(월평균소정근로시간 기본값 174h는 연간휴일 약 120일 가정의 대표값) */
    JP(174, 0.25, 0.25, 0.35, 5 * 60);

    /** 저녁 야간 시작(분) — 22:00, 양국 공통. */
    public static final int NIGHT_EVENING_START_MIN = 22 * 60;

    private final int hourlyDivisor;
    private final double overtimePremium;
    private final double nightPremium;
    private final double holidayPremium;
    private final int nightMorningEndMin;

    PayPolicy(int hourlyDivisor, double overtimePremium, double nightPremium, double holidayPremium,
            int nightMorningEndMin) {
        this.hourlyDivisor = hourlyDivisor;
        this.overtimePremium = overtimePremium;
        this.nightPremium = nightPremium;
        this.holidayPremium = holidayPremium;
        this.nightMorningEndMin = nightMorningEndMin;
    }

    /** 월 기본급 → 통상시급 환산 제수(월 소정근로시간). */
    public int hourlyDivisor() {
        return hourlyDivisor;
    }

    /** 연장근로 가산율(추가분). KR 0.5 / JP 0.25. */
    public double overtimePremium() {
        return overtimePremium;
    }

    /** 야간근로 가산율(추가분). KR 0.5 / JP 0.25. */
    public double nightPremium() {
        return nightPremium;
    }

    /** 휴일근로 가산율(추가분). KR 0.5 / JP 0.35. */
    public double holidayPremium() {
        return holidayPremium;
    }

    /** 아침 야간 종료(분) — KR 360(06:00) / JP 300(05:00). 저녁 시작은 NIGHT_EVENING_START_MIN 공통. */
    public int nightMorningEndMin() {
        return nightMorningEndMin;
    }

    /** 소재국 → 정책. ProfileCountry와 1:1(switch 완전 매칭 — 국가 추가 시 컴파일 에러로 확장 강제). */
    public static PayPolicy of(ProfileCountry country) {
        return switch (country) {
        case KR -> KR;
        case JP -> JP;
        };
    }

}
