package com.attendance.pro.leave;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.attendance.pro.tenant.ProfileCountry;

/**
 * 국가별 법정 연차 자동 산정 — 순수 함수 enum(BreakPolicy와 동형: DB/세션 무의존, 단위 테스트 대상).
 * 입력은 (입사일 hireDate, 기준일 asOf), 출력은 그 시점 부여할 <b>연차 일수</b>.
 *
 * ⚠ 법정 초안(딥리서치 차단으로 모델 지식 기반, docs/plan/admin-roles-and-leave.md §B). 방침이
 * "법정 자동 + 수동 조정"이라 회사 실무와 다르면 MANUAL 부여로 보정한다. 출근율 요건(KR 80% / JP 8할)은
 * 실제 출근율을 추적하지 않으므로 <b>충족 가정</b>한다.
 */
public enum LeaveAccrualPolicy {

    /**
     * 근로기준법 §60: 1년간 80% 출근 시 15일. 1년 미만은 1개월 개근당 1일(최대 11일).
     * 3년 이상 근속마다 가산(2년당 1일, 총 한도 25일). 가산 = (근속연수−1)/2, 3년차부터 +1.
     */
    KR {
        @Override
        public int entitledDays(LocalDate hireDate, LocalDate asOf) {
            if (hireDate == null || asOf.isBefore(hireDate)) {
                return 0;
            }
            long years = ChronoUnit.YEARS.between(hireDate, asOf);
            if (years < 1) {
                //1년 미만: 완성 개월수만큼 1일씩(최대 11)
                long months = ChronoUnit.MONTHS.between(hireDate, asOf);
                return (int) Math.min(11, Math.max(0, months));
            }
            long extra = Math.max(0, (years - 1) / 2); //3년차(years=3)부터 1일
            return (int) Math.min(25, 15 + extra);
        }
    },

    /**
     * 労働基準法 §39: 6개월 계속근무 + 8할 출근 시 10일. 이후 근속연수별 부여표
     * (1.5y 11 / 2.5y 12 / 3.5y 14 / 4.5y 16 / 5.5y 18 / 6.5y↑ 20). 6개월 미만은 0.
     */
    JP {
        @Override
        public int entitledDays(LocalDate hireDate, LocalDate asOf) {
            if (hireDate == null || asOf.isBefore(hireDate)) {
                return 0;
            }
            long months = ChronoUnit.MONTHS.between(hireDate, asOf);
            if (months >= 78) {
                return 20; //6.5년 이상
            }
            if (months >= 66) {
                return 18;
            }
            if (months >= 54) {
                return 16;
            }
            if (months >= 42) {
                return 14;
            }
            if (months >= 30) {
                return 12;
            }
            if (months >= 18) {
                return 11;
            }
            if (months >= 6) {
                return 10; //0.5년
            }
            return 0;
        }
    };

    /** 입사일·기준일로 그 시점 부여할 법정 연차 일수. */
    public abstract int entitledDays(LocalDate hireDate, LocalDate asOf);

    /** 소재국 → 정책(완전 매칭 — 국가 추가 시 컴파일 에러로 확장 지점 강제). */
    public static LeaveAccrualPolicy of(ProfileCountry country) {
        return switch (country) {
        case KR -> KR;
        case JP -> JP;
        };
    }
}
