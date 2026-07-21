package com.attendance.pro.attendance;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.attendance.AttendanceDtos.DailyAttendance;
import com.attendance.pro.attendance.AttendanceDtos.MonthlyResponse;
import com.attendance.pro.attendance.AttendanceDtos.PayrollSettlement;
import com.attendance.pro.attendance.PayrollCalculator.PayrollDay;
import com.attendance.pro.attendance.export.ReportSettingService;
import com.attendance.pro.tenant.ProfileCountry;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;

/**
 * 급여 정산(참고) 서비스 — 월별 출결(assembler 결과) + 멤버 월 기본급 + 국가별 PayPolicy + 회사 가산 설정을
 * 조합해 근태 기반 가감 명세를 만든다. 실지급이 아닌 참고값(4대보험·세금·수당 산입 별도).
 *
 * 야간(분)은 일자별 스케줄 구간과 야간대의 겹침을 실근무 비율로 환산한다(자정 넘김 스케줄 포함).
 * 휴일근로의 야간대는 스케줄이 없어 v1에서는 반영하지 않는다(참고 계산의 한계 — 필요 시 후속 정밀화).
 */
@Service
public class PayrollService {

    private final AttendanceService attendanceService;
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final ReportSettingService reportSettingService;

    public PayrollService(AttendanceService attendanceService, UserMapper userMapper,
            TenantMapper tenantMapper, ReportSettingService reportSettingService) {
        this.attendanceService = attendanceService;
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.reportSettingService = reportSettingService;
    }

    /** 급여 정산(참고). 월 기본급 미입력이면 null(호출부에서 "미입력" 표시). */
    @Transactional(readOnly = true)
    public PayrollSettlement settlement(long tenantId, long userId, int year, int month) {
        User user = userMapper.findById(tenantId, userId);
        if (user == null || user.baseMonthlySalary() == null) {
            return null;
        }
        Tenant tenant = tenantMapper.findById(tenantId);
        ProfileCountry country = tenant == null ? null : ProfileCountry.of(tenant.country());
        PayPolicy policy = PayPolicy.of(country == null ? ProfileCountry.KR : country); //미설정=KR(안전 폴백)
        boolean premiumApplied = reportSettingService.premiumEnabled(tenantId);

        MonthlyResponse monthly = attendanceService.monthly(tenantId, userId, year, month);
        List<PayrollDay> days = new ArrayList<>(monthly.days().size());
        for (DailyAttendance d : monthly.days()) {
            int worked = d.workMinutes() == null ? 0 : d.workMinutes();
            int sched = d.scheduledMinutes() == null ? 0 : d.scheduledMinutes();
            boolean restDay = d.holiday() || d.dayOff();
            boolean onLeave = d.leaveName() != null;
            int night = nightMinutes(d, worked, sched, policy);
            days.add(new PayrollDay(worked, sched, night, restDay, onLeave));
        }
        return PayrollCalculator.compute(user.baseMonthlySalary(), policy, premiumApplied, days);
    }

    /** 그 날 스케줄 구간과 야간대의 겹침(분)을 실근무 비율로 환산. 스케줄 없으면 0. */
    private int nightMinutes(DailyAttendance d, int worked, int sched, PayPolicy policy) {
        int start = parseHhmm(d.scheduleStart());
        int end = parseHhmm(d.scheduleEnd());
        if (start < 0 || end < 0) {
            return 0;
        }
        if (end <= start) {
            end += 1440; //안전 — 자정 넘김 스케줄은 조립기가 +24h로 주지만 방어
        }
        int overlap = PayrollCalculator.nightOverlapMinutes(start, end, policy.nightMorningEndMin());
        if (overlap == 0) {
            return 0;
        }
        double ratio = sched > 0 ? Math.min(1.0, worked / (double) sched) : (worked > 0 ? 1.0 : 0.0);
        return (int) Math.round(overlap * ratio);
    }

    /** "HH:mm"(HH는 24 이상 가능 — 자정 넘김 표기) → 분. null/형식 오류는 -1. */
    private int parseHhmm(String hhmm) {
        if (hhmm == null) {
            return -1;
        }
        int colon = hhmm.indexOf(':');
        if (colon <= 0) {
            return -1;
        }
        try {
            int h = Integer.parseInt(hhmm.substring(0, colon));
            int m = Integer.parseInt(hhmm.substring(colon + 1));
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

}
