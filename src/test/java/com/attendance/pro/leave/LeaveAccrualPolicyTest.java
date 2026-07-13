package com.attendance.pro.leave;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.attendance.pro.tenant.ProfileCountry;

/**
 * 법정 연차 자동 산정 경계 테스트(docs/plan/admin-roles-and-leave.md §B-1 초안).
 * 입사일→기준일 근속에 따른 부여 일수. 출근율 요건은 충족 가정.
 */
class LeaveAccrualPolicyTest {

    @ParameterizedTest(name = "KR: 입사 {0} → 기준 {1} = {2}일")
    @CsvSource({
            //hire, asOf, 기대 일수
            "2026-01-01, 2026-01-01, 0",   //ACC-KR-01: 입사 당일 0
            "2026-01-01, 2026-02-01, 1",   //ACC-KR-02: 1개월 개근 1일
            "2026-01-01, 2026-11-01, 10",  //ACC-KR-03: 10개월 10일
            "2026-01-01, 2026-12-01, 11",  //ACC-KR-04: 11개월 상한 11
            "2026-01-01, 2026-12-31, 11",  //ACC-KR-05: 1년 직전(11개월)도 11 상한 유지
            "2026-01-01, 2027-01-01, 15",  //ACC-KR-06: 만 1년 15
            "2025-01-01, 2027-01-01, 15",  //ACC-KR-07: 2년 15(가산 없음)
            "2024-01-01, 2027-01-01, 16",  //ACC-KR-08: 3년차 +1 = 16
            "2020-01-01, 2027-01-01, 18",  //ACC-KR-09: 7년 15+(7-1)/2=18
            "1990-01-01, 2027-01-01, 25",  //ACC-KR-10: 장기근속 상한 25
            "2026-01-31, 2026-02-28, 1",   //ACC-KR-11: 월말 입사 보정 — 2/28에 1개월 완성(1일)
            "2026-01-31, 2026-02-27, 0",   //ACC-KR-12: 2/27은 아직 1개월 미완성(0일)
    })
    void koreaAccrual(String hire, String asOf, int expected) {
        assertThat(LeaveAccrualPolicy.KR.entitledDays(LocalDate.parse(hire), LocalDate.parse(asOf)))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "JP: 입사 {0} → 기준 {1} = {2}일")
    @CsvSource({
            "2026-01-01, 2026-06-30, 0",   //ACC-JP-01: 6개월 미달 0
            "2026-01-01, 2026-07-01, 10",  //ACC-JP-02: 0.5년 10
            "2025-01-01, 2026-07-01, 11",  //ACC-JP-03: 1.5년 11
            "2024-01-01, 2026-07-01, 12",  //ACC-JP-04: 2.5년 12
            "2023-01-01, 2026-07-01, 14",  //ACC-JP-05: 3.5년 14
            "2022-01-01, 2026-07-01, 16",  //ACC-JP-06: 4.5년 16
            "2021-01-01, 2026-07-01, 18",  //ACC-JP-07: 5.5년 18
            "2020-01-01, 2026-07-01, 20",  //ACC-JP-08: 6.5년 20
            "2000-01-01, 2026-07-01, 20",  //ACC-JP-09: 장기근속 상한 20
    })
    void japanAccrual(String hire, String asOf, int expected) {
        assertThat(LeaveAccrualPolicy.JP.entitledDays(LocalDate.parse(hire), LocalDate.parse(asOf)))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("입사일 null / 기준일이 입사 전이면 0")
    void guardsNullAndBeforeHire() {
        assertThat(LeaveAccrualPolicy.KR.entitledDays(null, LocalDate.parse("2026-01-01")))
                .isZero();
        assertThat(LeaveAccrualPolicy.JP.entitledDays(LocalDate.parse("2026-01-01"),
                LocalDate.parse("2025-12-31"))).isZero();
    }

    @Test
    @DisplayName("ProfileCountry와 1:1 매핑(of)")
    void mapsFromProfileCountry() {
        assertThat(LeaveAccrualPolicy.of(ProfileCountry.KR)).isEqualTo(LeaveAccrualPolicy.KR);
        assertThat(LeaveAccrualPolicy.of(ProfileCountry.JP)).isEqualTo(LeaveAccrualPolicy.JP);
    }
}
