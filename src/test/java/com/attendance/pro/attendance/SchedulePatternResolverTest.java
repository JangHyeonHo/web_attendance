package com.attendance.pro.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 반복 패턴 → 날짜 투영(#13). 요일맵(cycle 1)·N주 주기(cycle 2)·휴무·야간을 검증.
 */
class SchedulePatternResolverTest {

    //2026-01-05 = 월요일(주기 원점)
    private static final LocalDate ANCHOR = LocalDate.of(2026, 1, 5);

    private static SchedulePattern pattern(int cycle) {
        return new SchedulePattern(1L, 10L, 7L, cycle, ANCHOR, true,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private static SchedulePatternSlot slot(int week, int dow, LocalTime start, LocalTime end) {
        return new SchedulePatternSlot(1L, week, dow, false, start, end, false);
    }

    @Test
    @DisplayName("cycle 1 요일맵: 월 09-18 슬롯 → 월요일 투영, 슬롯 없는 요일은 null(기본 폴백)")
    void weekdayMap() {
        var resolver = new SchedulePatternResolver(pattern(1),
                List.of(slot(0, 1, LocalTime.of(9, 0), LocalTime.of(18, 0))));

        WorkSchedule mon = resolver.resolve(LocalDate.of(2026, 1, 5)); //월
        assertThat(mon).isNotNull();
        assertThat(mon.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(mon.off()).isFalse();
        assertThat(resolver.resolve(LocalDate.of(2026, 1, 6))).isNull(); //화 — 슬롯 없음
    }

    @Test
    @DisplayName("cycle 2 격주: 0주차 월 09-18 / 1주차 월 10-19 — 주차가 번갈아 적용")
    void biweekly() {
        var resolver = new SchedulePatternResolver(pattern(2), List.of(
                slot(0, 1, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                slot(1, 1, LocalTime.of(10, 0), LocalTime.of(19, 0))));

        assertThat(resolver.resolve(LocalDate.of(2026, 1, 5)).startTime()).isEqualTo(LocalTime.of(9, 0));  //0주차 월
        assertThat(resolver.resolve(LocalDate.of(2026, 1, 12)).startTime()).isEqualTo(LocalTime.of(10, 0)); //1주차 월
        assertThat(resolver.resolve(LocalDate.of(2026, 1, 19)).startTime()).isEqualTo(LocalTime.of(9, 0));  //2주차=0주차 월
    }

    @Test
    @DisplayName("휴무·야간 슬롯 투영")
    void offAndOvernight() {
        var resolver = new SchedulePatternResolver(pattern(1), List.of(
                new SchedulePatternSlot(1L, 0, 6, true, null, null, false),                       //토 휴무
                new SchedulePatternSlot(1L, 0, 5, false, LocalTime.of(22, 0), LocalTime.of(6, 0), true))); //금 야간

        WorkSchedule sat = resolver.resolve(LocalDate.of(2026, 1, 10)); //토
        assertThat(sat.off()).isTrue();
        WorkSchedule fri = resolver.resolve(LocalDate.of(2026, 1, 9)); //금
        assertThat(fri.crossesMidnight()).isTrue();
        assertThat(fri.endTime()).isEqualTo(LocalTime.of(6, 0));
    }
}
