package com.attendance.pro.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.attendance.pro.attendance.AttendanceDtos.DailyAttendance;

/**
 * Phase 5 집계 변경 테스트 — 휴일·휴무 근무 채용 / 요일 휴무(dayOff) / 수동 정정 마커.
 * 달력: 2026-07-01(수)~07-05(일). 주5일 플래그 '1111100'이면 07-04(토)·07-05(일)이 휴무.
 */
class Phase5AssemblerTest {

    private final MonthlyAttendanceAssembler assembler = new MonthlyAttendanceAssembler();
    private final AtomicLong idSeq = new AtomicLong(1);

    private static final LocalDate WED = LocalDate.of(2026, 7, 1);
    private static final LocalDate SAT = LocalDate.of(2026, 7, 4);
    private static final LocalDate SUN = LocalDate.of(2026, 7, 5);
    private static final List<LocalDate> WEEK = WED.datesUntil(SUN.plusDays(1)).toList();

    private AttendanceStamp stamp(AttendanceType type, LocalDateTime at) {
        return new AttendanceStamp(idSeq.getAndIncrement(), 1L, type.code(), 0, at,
                StampSource.AUTO, null, null);
    }

    private AttendanceStamp manualStamp(AttendanceType type, LocalDateTime at) {
        return new AttendanceStamp(idSeq.getAndIncrement(), 1L, type.code(), 0, at,
                StampSource.MANUAL, "FORGOT", null);
    }

    private DailyAttendance dayOf(List<DailyAttendance> days, LocalDate date) {
        return days.stream().filter(d -> d.date().equals(date)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("공휴일 근무: 스탬프가 채용되고 법정휴게는 실체류 기반(9h→KR 60분), 합계 포함")
    void holidayWorkAdopted() {
        List<DailyAttendance> days = assembler.assemble(WEEK, Map.of(), Map.of(WED, "창립기념일"),
                List.of(stamp(AttendanceType.GO_TO_WORK, WED.atTime(9, 0)),
                        stamp(AttendanceType.OFF_WORK, WED.atTime(18, 0))),
                null, null, "1111100", BreakPolicy.KR);

        DailyAttendance day = dayOf(days, WED);
        assertThat(day.holiday()).isTrue();
        assertThat(day.holidayName()).isEqualTo("창립기념일");
        assertThat(day.scheduleStart()).isNull(); //휴일에 스케줄은 없다
        assertThat(day.stampIn()).isEqualTo("09:00");
        assertThat(day.stampOut()).isEqualTo("18:00");
        assertThat(day.statutoryBreakMinutes()).isEqualTo(60); //실체류 9h 기반
        assertThat(day.workMinutes()).isEqualTo(480);
    }

    @Test
    @DisplayName("공휴일에 스탬프가 없으면 기존과 동일하게 전부 공란")
    void holidayWithoutStampsStaysBlank() {
        List<DailyAttendance> days = assembler.assemble(WEEK, Map.of(), Map.of(WED, "창립기념일"),
                List.of(), null, null, "1111100", BreakPolicy.KR);

        DailyAttendance day = dayOf(days, WED);
        assertThat(day.holiday()).isTrue();
        assertThat(day.stampIn()).isNull();
        assertThat(day.statutoryBreakMinutes()).isNull();
        assertThat(day.workMinutes()).isNull();
    }

    @Test
    @DisplayName("요일 휴무: 주5일 플래그면 토·일이 dayOff — 스탬프가 있으면 채용·산출된다")
    void weekendDayOff() {
        List<DailyAttendance> days = assembler.assemble(WEEK, Map.of(), Map.of(),
                List.of(stamp(AttendanceType.GO_TO_WORK, SAT.atTime(10, 0)),
                        stamp(AttendanceType.OFF_WORK, SAT.atTime(15, 0))),
                null, null, "1111100", BreakPolicy.KR);

        DailyAttendance sat = dayOf(days, SAT);
        assertThat(sat.dayOff()).isTrue();
        assertThat(sat.holiday()).isFalse();
        assertThat(sat.scheduleStart()).isNull();
        assertThat(sat.stampIn()).isEqualTo("10:00");
        assertThat(sat.statutoryBreakMinutes()).isEqualTo(30); //5h 실체류 → KR 30분
        assertThat(sat.workMinutes()).isEqualTo(270);

        DailyAttendance sun = dayOf(days, SUN);
        assertThat(sun.dayOff()).isTrue();
        assertThat(sun.stampIn()).isNull();
        assertThat(sun.workMinutes()).isNull();

        //평일은 기존 그대로 근무일
        assertThat(dayOf(days, WED).dayOff()).isFalse();
        assertThat(dayOf(days, WED).scheduleStart()).isEqualTo("09:00");
    }

    @Test
    @DisplayName("요일 휴무보다 일자 오버라이드가 우선 — 토요일에 스케줄이 등록되면 근무일")
    void overrideBeatsDayOff() {
        WorkSchedule override = new WorkSchedule(1L, 1L, SAT, LocalTime.of(10, 0), LocalTime.of(14, 0), false, false, false);
        List<DailyAttendance> days = assembler.assemble(WEEK, Map.of(SAT, override), Map.of(),
                List.of(), null, null, "1111100", BreakPolicy.KR);

        DailyAttendance sat = dayOf(days, SAT);
        assertThat(sat.dayOff()).isFalse();
        assertThat(sat.scheduleStart()).isEqualTo("10:00");
        assertThat(sat.scheduleEnd()).isEqualTo("14:00");
        assertThat(sat.statutoryBreakMinutes()).isEqualTo(30); //4h 스케줄 — KR 30분
    }

    @Test
    @DisplayName("workDays 결손(null)은 전 요일 근무로 해석 — 기존 동작 불변(방어)")
    void nullWorkDaysMeansAllWorkdays() {
        List<DailyAttendance> days = assembler.assemble(WEEK, Map.of(), Map.of(),
                List.of(), null, null, null, BreakPolicy.KR);

        assertThat(dayOf(days, SAT).dayOff()).isFalse();
        assertThat(dayOf(days, SAT).scheduleStart()).isEqualTo("09:00");
    }

    @Test
    @DisplayName("수동 정정 마커: MANUAL 스탬프가 있는 날만 manual=true")
    void manualMarker() {
        List<DailyAttendance> days = assembler.assemble(WEEK, Map.of(), Map.of(),
                List.of(stamp(AttendanceType.GO_TO_WORK, WED.atTime(9, 0)),
                        manualStamp(AttendanceType.OFF_WORK, WED.atTime(18, 0)),
                        stamp(AttendanceType.GO_TO_WORK, WED.plusDays(1).atTime(9, 0)),
                        stamp(AttendanceType.OFF_WORK, WED.plusDays(1).atTime(18, 0))),
                null, null, "1111100", BreakPolicy.KR);

        assertThat(dayOf(days, WED).manual()).isTrue();
        assertThat(dayOf(days, WED).stampOut()).isEqualTo("18:00"); //수동 스탬프도 채용 규칙은 동일
        assertThat(dayOf(days, WED.plusDays(1)).manual()).isFalse();
    }

}
