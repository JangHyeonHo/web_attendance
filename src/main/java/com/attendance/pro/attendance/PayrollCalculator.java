package com.attendance.pro.attendance;

import java.util.List;

import com.attendance.pro.attendance.AttendanceDtos.PayrollSettlement;

/**
 * 급여 정산(참고) 계산기 — 순수 함수 클래스(DB/세션 의존 없음, 단위 테스트 대상. 조립기·BreakPolicy와 동형).
 *
 * 월 기본급이 "소정근로시간"을 이미 보상한다는 전제(월급제)에서 그날그날의 근태로 다음을 가감한다:
 *  - (＋) 연장근로  : 예정 대비 초과 근무 = workedMin − schedMin(&gt;0). 통상시급 ×(1+연장가산).
 *  - (＋) 휴일근로  : 휴일·휴무일(restDay)의 실근무 전부. 통상시급 ×(1+휴일가산).
 *  - (＋) 야간가산  : 실근무가 야간대(22:00~아침)와 겹친 시간 × 통상시급 × 야간가산(가산분만).
 *  - (－) 무노동공제 : 소정근로일(휴가·휴일 아님)의 미달분 = schedMin − workedMin(&gt;0). 통상시급 ×1.0.
 *                     지각·조퇴·결근을 "안 일한 만큼만" 공제(무노동무임금 §43 / ノーワークノーペイ). 벌금식 X.
 *
 * premiumApplied=false(상시 5인 미만 사업장 등 §56 가산 미대상): 연장·휴일은 가산 없이 ×1.0, 야간가산 0.
 * 통상시급 = round(월기본급 ÷ 국가별 divisor). 미입력(salary=null)·정책 없음이면 정산 불가(null 반환).
 * 결과는 "참고"이며 실지급·4대보험·세금·통상임금 산입범위(수당)는 별도 급여시스템 소관.
 */
public final class PayrollCalculator {

    private PayrollCalculator() {
    }

    /**
     * 급여 정산(참고) 산출. baseMonthlySalary가 null이면 계산 불가(null 반환 — 호출부에서 "급여 미입력" 처리).
     *
     * @param baseMonthlySalary 월 기본급(원/円). null=미입력.
     * @param policy            국가별 정책(가산율·야간대·divisor).
     * @param premiumApplied    §56 가산 적용 여부(회사 설정 — 5인 미만이면 false).
     * @param days              그 달의 일자별 근태 요약.
     */
    public static PayrollSettlement compute(Long baseMonthlySalary, PayPolicy policy,
            boolean premiumApplied, List<PayrollDay> days) {
        if (baseMonthlySalary == null || policy == null) {
            return null;
        }
        long hourly = Math.round((double) baseMonthlySalary / policy.hourlyDivisor());

        long overtimeMin = 0;
        long holidayWorkMin = 0;
        long nightMin = 0;
        long shortfallMin = 0;
        for (PayrollDay d : days) {
            int worked = Math.max(0, d.workedMin());
            int sched = Math.max(0, d.schedMin());
            if (d.restDay()) {
                holidayWorkMin += worked; //휴일·휴무일의 근무는 전부 휴일근로
            } else {
                if (worked > sched) {
                    overtimeMin += (worked - sched); //예정 초과분 = 연장
                }
                if (!d.onLeave() && sched > worked) {
                    shortfallMin += (sched - worked); //소정근로일의 미달분 = 무노동 공제 대상
                }
            }
            nightMin += Math.max(0, d.nightMin()); //야간대 실근무(휴일/평일 무관 가산)
        }

        double otFactor = premiumApplied ? (1.0 + policy.overtimePremium()) : 1.0;
        double holidayFactor = premiumApplied ? (1.0 + policy.holidayPremium()) : 1.0;
        double nightFactor = premiumApplied ? policy.nightPremium() : 0.0; //가산분만

        long overtimePay = money(overtimeMin, hourly, otFactor);
        long holidayPay = money(holidayWorkMin, hourly, holidayFactor);
        long nightPay = money(nightMin, hourly, nightFactor);
        long deduction = money(shortfallMin, hourly, 1.0);
        long netAdjustment = overtimePay + holidayPay + nightPay - deduction;

        return new PayrollSettlement(
                policy.name(), baseMonthlySalary, hourly, premiumApplied,
                (int) overtimeMin, (int) nightMin, (int) holidayWorkMin, (int) shortfallMin,
                overtimePay, nightPay, holidayPay, deduction, netAdjustment);
    }

    /** 분 × 통상시급 × 배수 → 원(반올림). */
    private static long money(long minutes, long hourly, double factor) {
        return Math.round(minutes / 60.0 * hourly * factor);
    }

    /**
     * 야간대 겹침(분) 계산 — 실근무 구간 [startMin, endMin)이 야간대와 겹치는 분.
     * startMin은 0~1439(그날 시업), endMin은 자정 넘김 시 1440 초과 가능(≤2일 구간).
     * 야간대는 매일 저녁 [22:00, 24:00) ∪ 아침 [00:00, morningEnd) 반복.
     */
    public static int nightOverlapMinutes(int startMin, int endMin, int morningEnd) {
        int night = 0;
        int t = startMin;
        while (t < endMin) {
            int dayStart = (t / 1440) * 1440;
            int segEnd = Math.min(endMin, dayStart + 1440);
            int a = t - dayStart;             //그 날 조각의 시업(분, 0~1439)
            int b = a + (segEnd - t);         //그 날 조각의 종업(분)
            night += overlap(a, b, PayPolicy.NIGHT_EVENING_START_MIN, 1440); //저녁 22:00~24:00
            night += overlap(a, b, 0, morningEnd);                            //아침 00:00~morningEnd
            t = segEnd;
        }
        return night;
    }

    private static int overlap(int a, int b, int x, int y) {
        return Math.max(0, Math.min(b, y) - Math.max(a, x));
    }

    /** 정산 계산 입력(일자별 근태 요약). nightMin은 야간대 실근무(분). restDay=휴일·휴무, onLeave=승인 휴가일. */
    public record PayrollDay(int workedMin, int schedMin, int nightMin, boolean restDay, boolean onLeave) {
    }

}
