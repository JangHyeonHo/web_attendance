package com.attendance.pro.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.attendance.pro.attendance.AttendanceDtos.PayrollSettlement;
import com.attendance.pro.attendance.PayrollCalculator.PayrollDay;

/**
 * 급여 정산 계산기 단위 테스트 — 국가별 가산율·야간대·divisor + 무노동 공제 검증(순수 함수).
 */
class PayrollCalculatorTest {

    // 연장 120분 / 미달 60분 / 휴일근무 480분 / 야간 480분 시나리오
    private static List<PayrollDay> scenario() {
        return List.of(
                new PayrollDay(600, 480, 0, false, false),   // 연장 120
                new PayrollDay(420, 480, 0, false, false),   // 미달 60(무노동 공제)
                new PayrollDay(480, 0, 0, true, false),      // 휴일근로 480
                new PayrollDay(480, 480, 480, false, false)  // 야간 480(정상근무)
        );
    }

    @Test
    void korea_premiumsAndDeduction() {
        PayrollSettlement s = PayrollCalculator.compute(3_000_000L, PayPolicy.KR, true, scenario());

        assertThat(s.country()).isEqualTo("KR");
        assertThat(s.hourlyWage()).isEqualTo(Math.round(3_000_000.0 / 209)); // 14354
        assertThat(s.overtimeMinutes()).isEqualTo(120);
        assertThat(s.shortfallMinutes()).isEqualTo(60);
        assertThat(s.holidayWorkMinutes()).isEqualTo(480);
        assertThat(s.nightMinutes()).isEqualTo(480);

        long hourly = s.hourlyWage();
        assertThat(s.overtimePay()).isEqualTo(Math.round(120 / 60.0 * hourly * 1.5));
        assertThat(s.holidayPay()).isEqualTo(Math.round(480 / 60.0 * hourly * 1.5));
        assertThat(s.nightPay()).isEqualTo(Math.round(480 / 60.0 * hourly * 0.5));
        assertThat(s.deduction()).isEqualTo(Math.round(60 / 60.0 * hourly * 1.0));
        assertThat(s.netAdjustment())
                .isEqualTo(s.overtimePay() + s.holidayPay() + s.nightPay() - s.deduction());
    }

    @Test
    void japan_lowerPremiumsAndDivisor() {
        PayrollSettlement s = PayrollCalculator.compute(3_000_000L, PayPolicy.JP, true, scenario());

        assertThat(s.hourlyWage()).isEqualTo(Math.round(3_000_000.0 / 174)); // divisor 174
        long hourly = s.hourlyWage();
        assertThat(s.overtimePay()).isEqualTo(Math.round(120 / 60.0 * hourly * 1.25)); // +25%
        assertThat(s.holidayPay()).isEqualTo(Math.round(480 / 60.0 * hourly * 1.35)); // +35%
        assertThat(s.nightPay()).isEqualTo(Math.round(480 / 60.0 * hourly * 0.25));   // +25%
    }

    @Test
    void premiumOff_noGasanButStillPaysWorkedAndDeducts() {
        // 5인 미만 등 가산 미적용 — 연장·휴일은 ×1.0, 야간가산 0, 공제는 그대로
        PayrollSettlement s = PayrollCalculator.compute(3_000_000L, PayPolicy.KR, false, scenario());
        long hourly = s.hourlyWage();
        assertThat(s.overtimePay()).isEqualTo(Math.round(120 / 60.0 * hourly * 1.0));
        assertThat(s.holidayPay()).isEqualTo(Math.round(480 / 60.0 * hourly * 1.0));
        assertThat(s.nightPay()).isEqualTo(0L);
        assertThat(s.deduction()).isEqualTo(Math.round(60 / 60.0 * hourly * 1.0));
    }

    @Test
    void nullSalary_returnsNull() {
        assertThat(PayrollCalculator.compute(null, PayPolicy.KR, true, scenario())).isNull();
    }

    @Test
    void onLeaveDay_notDeductedAsShortfall() {
        // 승인 휴가일은 미근로여도 무노동 공제 대상이 아님
        List<PayrollDay> days = List.of(new PayrollDay(0, 480, 0, false, true));
        PayrollSettlement s = PayrollCalculator.compute(3_000_000L, PayPolicy.KR, true, days);
        assertThat(s.shortfallMinutes()).isZero();
        assertThat(s.deduction()).isZero();
    }

    @Test
    void nightOverlap_overnightShift() {
        // 22:00~06:00(KR morningEnd 360) → 120(저녁) + 360(아침) = 480
        assertThat(PayrollCalculator.nightOverlapMinutes(22 * 60, 30 * 60, 360)).isEqualTo(480);
        // 일본(morningEnd 300) → 120 + 300 = 420
        assertThat(PayrollCalculator.nightOverlapMinutes(22 * 60, 30 * 60, 300)).isEqualTo(420);
        // 02:00~06:00 → 아침 [0,360) 겹침 240, 저녁 0
        assertThat(PayrollCalculator.nightOverlapMinutes(120, 360, 360)).isEqualTo(240);
        // 09:00~18:00 주간 → 0
        assertThat(PayrollCalculator.nightOverlapMinutes(9 * 60, 18 * 60, 360)).isZero();
    }
}
