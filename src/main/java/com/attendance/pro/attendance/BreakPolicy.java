package com.attendance.pro.attendance;

import java.time.Duration;

import com.attendance.pro.tenant.ProfileCountry;

/**
 * 국가별 법정 휴게 산출 전략 — 순수 함수 enum. DB/세션 의존 없음(조립기와 동일하게 단위 테스트 대상).
 * 입력은 "스케줄 근무구간 길이"(종업-시업 — work-schedule §1-1), 출력은 그날 부여할 최소 휴게.
 * 경계값 포함 여부는 각 법 조문의 문언("이상/인 경우" vs "초과")을 그대로 따른다(§2-2).
 * ProfileCountry에 얹지 않는 이유: 국가 축은 공유하되 규칙은 도메인별 분리(식별번호≠근태).
 */
public enum BreakPolicy {

    /** 근로기준법 제54조: 근로시간 4시간인 경우 30분 이상, 8시간인 경우 1시간 이상 — "인 경우"는 경계 포함 */
    KR {
        @Override
        public Duration requiredBreak(Duration scheduledWork) {
            if (scheduledWork.compareTo(Duration.ofHours(8)) >= 0) {
                return Duration.ofMinutes(60);
            }
            if (scheduledWork.compareTo(Duration.ofHours(4)) >= 0) {
                return Duration.ofMinutes(30);
            }
            return Duration.ZERO;
        }
    },

    /** 労働基準法 第34条: 6시간을 "초과"하면 45분 이상, 8시간을 "초과"하면 1시간 이상 — 경계 미포함 */
    JP {
        @Override
        public Duration requiredBreak(Duration scheduledWork) {
            if (scheduledWork.compareTo(Duration.ofHours(8)) > 0) {
                return Duration.ofMinutes(60);
            }
            if (scheduledWork.compareTo(Duration.ofHours(6)) > 0) {
                return Duration.ofMinutes(45);
            }
            return Duration.ZERO;
        }
    };

    public abstract Duration requiredBreak(Duration scheduledWork);

    /** 소재국 → 정책. ProfileCountry와 1:1(switch 완전 매칭 — 국가 추가 시 컴파일 에러로 확장 지점 강제). */
    public static BreakPolicy of(ProfileCountry country) {
        return switch (country) {
        case KR -> KR;
        case JP -> JP;
        };
    }

}
