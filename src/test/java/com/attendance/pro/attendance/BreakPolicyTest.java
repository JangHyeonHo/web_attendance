package com.attendance.pro.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.attendance.pro.tenant.ProfileCountry;

/**
 * 국가별 법정 휴게 경계값 테스트 — BRK-01~07(work-schedule §2-2 확정표 그대로).
 */
class BreakPolicyTest {

    @ParameterizedTest(name = "BRK: {0}분 스케줄 → KR {1}분 / JP {2}분")
    @CsvSource({
            //스케줄(분), KR 기대(분), JP 기대(분) — §2-2 표의 7행
            "239, 0,  0",    //BRK-01: 3h59m — 양국 모두 미달
            "240, 30, 0",    //BRK-02: 정확히 4h — KR '인 경우' 포함 / JP 미달
            "360, 30, 0",    //BRK-03: 정확히 6h — JP '초과' 미포함
            "361, 30, 45",   //BRK-04: 6h01m — JP 초과 성립
            "480, 60, 45",   //BRK-05: 정확히 8h — KR 포함 / JP 미포함
            "481, 60, 60",   //BRK-06: 8h01m — 양국 상한 구간
            "540, 60, 60",   //BRK-07: 9h(기본 09~18)의 기대값
    })
    void boundaryTable(long scheduledMinutes, long expectedKr, long expectedJp) {
        Duration scheduled = Duration.ofMinutes(scheduledMinutes);
        assertThat(BreakPolicy.KR.requiredBreak(scheduled).toMinutes()).isEqualTo(expectedKr);
        assertThat(BreakPolicy.JP.requiredBreak(scheduled).toMinutes()).isEqualTo(expectedJp);
    }

    @Test
    @DisplayName("ProfileCountry와 1:1 매핑(of) — 소재국이 정책을 결정한다")
    void mapsFromProfileCountry() {
        assertThat(BreakPolicy.of(ProfileCountry.KR)).isEqualTo(BreakPolicy.KR);
        assertThat(BreakPolicy.of(ProfileCountry.JP)).isEqualTo(BreakPolicy.JP);
    }

    @Test
    @DisplayName("X9 방어: 0분 스케줄(start=end)은 법정휴게 0")
    void zeroScheduleYieldsZero() {
        assertThat(BreakPolicy.KR.requiredBreak(Duration.ZERO)).isEqualTo(Duration.ZERO);
        assertThat(BreakPolicy.JP.requiredBreak(Duration.ZERO)).isEqualTo(Duration.ZERO);
    }

}
