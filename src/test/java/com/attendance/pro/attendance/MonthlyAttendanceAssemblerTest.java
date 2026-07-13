package com.attendance.pro.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.attendance.pro.attendance.AttendanceDtos.DailyAttendance;

/**
 * 월별 출결 조립(스케쥴 x 스탬프 페어링 + 실휴식/법정휴게/총 근무시간) 테스트.
 * 기존 8건은 시그니처 변경만 반영(기대값 불변) + CALC-01~08 / PAIR-01~05 / HOL-01.
 */
class MonthlyAttendanceAssemblerTest {

    private final MonthlyAttendanceAssembler assembler = new MonthlyAttendanceAssembler();
    private final AtomicLong idSeq = new AtomicLong(1);

    private static final LocalDate D1 = LocalDate.of(2026, 7, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 7, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 7, 3);

    private AttendanceStamp stamp(AttendanceType type, LocalDateTime at) {
        return new AttendanceStamp(idSeq.getAndIncrement(), 1L, type.code(), 0, at, StampSource.AUTO, null, null);
    }

    private AttendanceStamp breakStart(LocalDateTime at) {
        return new AttendanceStamp(idSeq.getAndIncrement(), 1L, AttendanceType.BREAK.code(),
                AttendanceStamp.STATUS_ACTIVE, at, StampSource.AUTO, null, null);
    }

    private AttendanceStamp breakEnd(LocalDateTime at) {
        return new AttendanceStamp(idSeq.getAndIncrement(), 1L, AttendanceType.BREAK.code(),
                AttendanceStamp.STATUS_BREAK_ENDED, at, StampSource.AUTO, null, null);
    }

    private List<DailyAttendance> assemble(List<AttendanceStamp> stamps) {
        return assembler.assemble(List.of(D1, D2, D3), Map.of(), Map.of(), stamps,
                null, null, null, BreakPolicy.KR);
    }

    private List<DailyAttendance> assembleKr(Map<LocalDate, WorkSchedule> schedules,
            List<AttendanceStamp> stamps) {
        return assembler.assemble(List.of(D1, D2, D3), schedules, Map.of(), stamps,
                null, null, null, BreakPolicy.KR);
    }

    @Nested
    @DisplayName("기존 페어링 규칙(회귀 — 기대값 불변)")
    class LegacyPairing {

        @Test
        @DisplayName("정상 출퇴근: 같은 날 출근/퇴근이 페어링된다")
        void normalDay() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 12)),
                    stamp(AttendanceType.OFF_WORK, D1.atTime(18, 3))));

            assertThat(days.get(0).stampIn()).isEqualTo("09:12");
            assertThat(days.get(0).stampOut()).isEqualTo("18:03");
            assertThat(days.get(1).stampIn()).isNull();
            assertThat(days.get(0).scheduleStart()).isEqualTo("09:00");
            assertThat(days.get(0).scheduleEnd()).isEqualTo("18:00");
        }

        @Test
        @DisplayName("야근: 다음날 새벽 퇴근은 24+시로 표기된다")
        void overnight() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(13, 0)),
                    stamp(AttendanceType.OFF_WORK, D2.atTime(1, 10)),
                    stamp(AttendanceType.GO_TO_WORK, D2.atTime(9, 0)),
                    stamp(AttendanceType.OFF_WORK, D2.atTime(18, 0))));

            assertThat(days.get(0).stampIn()).isEqualTo("13:00");
            assertThat(days.get(0).stampOut()).isEqualTo("25:10");
            //다음날 스탬프는 다음날 행에 페어링된다
            assertThat(days.get(1).stampIn()).isEqualTo("09:00");
            assertThat(days.get(1).stampOut()).isEqualTo("18:00");
        }

        @Test
        @DisplayName("미퇴근: 48시간 넘게 퇴근이 없으면 퇴근 공란")
        void noOffWorkOver48h() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    stamp(AttendanceType.OFF_WORK, D3.atTime(10, 0))));

            assertThat(days.get(0).stampIn()).isEqualTo("09:00");
            assertThat(days.get(0).stampOut()).isNull();
            //CALC-08: 미퇴근 일자는 break/work 모두 null, 법정휴게는 스케줄 기반이므로 표시(X2)
            assertThat(days.get(0).breakMinutes()).isNull();
            assertThat(days.get(0).workMinutes()).isNull();
            assertThat(days.get(0).statutoryBreakMinutes()).isEqualTo(60);
        }

        @Test
        @DisplayName("출근 연속: 다음날 또 출근이 찍히면 전날은 미퇴근 처리")
        void consecutiveGoToWork() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    stamp(AttendanceType.GO_TO_WORK, D2.atTime(9, 30)),
                    stamp(AttendanceType.OFF_WORK, D2.atTime(18, 0))));

            assertThat(days.get(0).stampIn()).isEqualTo("09:00");
            assertThat(days.get(0).stampOut()).isNull();
            assertThat(days.get(1).stampIn()).isEqualTo("09:30");
            assertThat(days.get(1).stampOut()).isEqualTo("18:00");
        }

        @Test
        @DisplayName("같은 날 중복 스탬프는 마지막 값이 남는다(재출근/퇴근 덮어쓰기)")
        void sameDayOverwrite() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    stamp(AttendanceType.OFF_WORK, D1.atTime(12, 0)),
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(13, 0)),
                    stamp(AttendanceType.OFF_WORK, D1.atTime(18, 0))));

            assertThat(days.get(0).stampIn()).isEqualTo("13:00");
            assertThat(days.get(0).stampOut()).isEqualTo("18:00");
        }

        @Test
        @DisplayName("휴일: 공휴일/개인휴일은 스케쥴과 스탬프 모두 공란(신규 3필드도 null)")
        void holidays() {
            WorkSchedule personalHoliday = new WorkSchedule(1L, 1L, D2, null, null, true);
            List<DailyAttendance> days = assembler.assemble(
                    List.of(D1, D2, D3),
                    Map.of(D2, personalHoliday),
                    Map.of(D1, "삼일절"),
                    List.of(stamp(AttendanceType.GO_TO_WORK, D3.atTime(9, 0))),
                    null, null, null, BreakPolicy.KR);

            assertThat(days.get(0).holiday()).isTrue();
            assertThat(days.get(0).scheduleStart()).isNull();
            assertThat(days.get(0).breakMinutes()).isNull();
            assertThat(days.get(0).statutoryBreakMinutes()).isNull();
            assertThat(days.get(0).workMinutes()).isNull();
            assertThat(days.get(1).holiday()).isTrue();
            assertThat(days.get(2).holiday()).isFalse();
            assertThat(days.get(2).stampIn()).isEqualTo("09:00");
        }

        @Test
        @DisplayName("스케쥴 오버라이드: 등록된 일자는 해당 시업/종업 시각이 표시된다")
        void scheduleOverride() {
            WorkSchedule override = new WorkSchedule(1L, 1L, D1, LocalTime.of(10, 30), LocalTime.of(19, 30), false);
            List<DailyAttendance> days = assembler.assemble(
                    List.of(D1, D2), Map.of(D1, override), Map.of(), List.of(),
                    null, null, null, BreakPolicy.KR);

            assertThat(days.get(0).scheduleStart()).isEqualTo("10:30");
            assertThat(days.get(0).scheduleEnd()).isEqualTo("19:30");
            assertThat(days.get(1).scheduleStart()).isEqualTo("09:00");
        }

        @Test
        @DisplayName("스탬프가 없으면 스케쥴만 표시된다")
        void emptyStamps() {
            List<DailyAttendance> days = assemble(List.of());
            assertThat(days).hasSize(3);
            assertThat(days).allSatisfy(d -> {
                assertThat(d.stampIn()).isNull();
                assertThat(d.stampOut()).isNull();
                assertThat(d.workMinutes()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("총 근무시간 계산(CALC — §1-2 수치 검증)")
    class Calc {

        @Test
        @DisplayName("CALC-01(E1): KR 정시 — 휴식 60분이면 총 8h(480분)")
        void kr9to18WithLunch() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    breakStart(D1.atTime(12, 0)),
                    breakEnd(D1.atTime(13, 0)),
                    stamp(AttendanceType.OFF_WORK, D1.atTime(18, 0))));

            assertThat(days.get(0).breakMinutes()).isEqualTo(60);
            assertThat(days.get(0).statutoryBreakMinutes()).isEqualTo(60);
            assertThat(days.get(0).workMinutes()).isEqualTo(480);
        }

        @Test
        @DisplayName("CALC-02(E2): 휴식 초과 90분 — 출퇴근 동일, 총계만 450분")
        void excessBreakReducesWork() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    breakStart(D1.atTime(12, 0)),
                    breakEnd(D1.atTime(13, 30)),
                    stamp(AttendanceType.OFF_WORK, D1.atTime(18, 0))));

            assertThat(days.get(0).breakMinutes()).isEqualTo(90);
            assertThat(days.get(0).workMinutes()).isEqualTo(450);
        }

        @Test
        @DisplayName("CALC-03(E3): 휴식 미기록도 법정휴게는 차감(breakMinutes=0, work=480)")
        void statutoryDeductedWithoutRecordedBreak() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    stamp(AttendanceType.OFF_WORK, D1.atTime(18, 0))));

            assertThat(days.get(0).breakMinutes()).isEqualTo(0);
            assertThat(days.get(0).statutoryBreakMinutes()).isEqualTo(60);
            assertThat(days.get(0).workMinutes()).isEqualTo(480);
        }

        @Test
        @DisplayName("CALC-04(E4): JP 6h 정각 스케줄은 법정휴게 0 — 실휴식 20분만 차감(340분)")
        void jp6hBoundary() {
            WorkSchedule schedule = new WorkSchedule(1L, 1L, D1, LocalTime.of(9, 0), LocalTime.of(15, 0), false);
            List<DailyAttendance> days = assembler.assemble(
                    List.of(D1), Map.of(D1, schedule), Map.of(),
                    List.of(
                            stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                            breakStart(D1.atTime(12, 0)),
                            breakEnd(D1.atTime(12, 20)),
                            stamp(AttendanceType.OFF_WORK, D1.atTime(15, 0))),
                    null, null, null, BreakPolicy.JP);

            assertThat(days.get(0).statutoryBreakMinutes()).isEqualTo(0);
            assertThat(days.get(0).breakMinutes()).isEqualTo(20);
            assertThat(days.get(0).workMinutes()).isEqualTo(340);
        }

        @Test
        @DisplayName("CALC-05(E6): 야근 — 새벽 휴식 포함 창 기준 합산(표기 25:10 유지, 890분)")
        void overnightWithBreaks() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    breakStart(D1.atTime(12, 0)),
                    breakEnd(D1.atTime(13, 0)),
                    breakStart(D1.atTime(19, 0)),
                    breakEnd(D1.atTime(19, 20)),
                    stamp(AttendanceType.OFF_WORK, D2.atTime(1, 10))));

            assertThat(days.get(0).stampOut()).isEqualTo("25:10");
            assertThat(days.get(0).breakMinutes()).isEqualTo(80);
            assertThat(days.get(0).statutoryBreakMinutes()).isEqualTo(60);
            assertThat(days.get(0).workMinutes()).isEqualTo(890);
        }

        @Test
        @DisplayName("CALC-06(E7): 반차 오버라이드(09~13) — KR 30분 차감(210분)")
        void halfDayOverride() {
            WorkSchedule halfDay = new WorkSchedule(1L, 1L, D1, LocalTime.of(9, 0), LocalTime.of(13, 0), false);
            List<DailyAttendance> days = assembleKr(Map.of(D1, halfDay), List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    stamp(AttendanceType.OFF_WORK, D1.atTime(13, 0))));

            assertThat(days.get(0).statutoryBreakMinutes()).isEqualTo(30);
            assertThat(days.get(0).workMinutes()).isEqualTo(210);
        }

        @Test
        @DisplayName("REC-01: 인정 휴게 = max(법정, 실휴식) — 휴식 미기록/부족/초과 3케이스(KR 8h 근무)")
        void recognizedBreak() {
            //09~18 스케줄(KR 법정 60분). 실체류 540분.
            WorkSchedule day = new WorkSchedule(1L, 1L, D1, LocalTime.of(9, 0), LocalTime.of(18, 0), false);

            //휴식 미기록 → 법정 60 자동 인정, 근무 480
            List<DailyAttendance> none = assembler.assemble(List.of(D1), Map.of(D1, day), Map.of(),
                    List.of(stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                            stamp(AttendanceType.OFF_WORK, D1.atTime(18, 0))),
                    null, null, null, BreakPolicy.KR);
            assertThat(none.get(0).breakMinutes()).isZero();
            assertThat(none.get(0).recognizedBreakMinutes()).isEqualTo(60);
            assertThat(none.get(0).workMinutes()).isEqualTo(480);

            //45분 기록(법정 미달) → 60 인정, 근무 480
            List<DailyAttendance> under = assembler.assemble(List.of(D1), Map.of(D1, day), Map.of(),
                    List.of(stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                            breakStart(D1.atTime(12, 0)), breakEnd(D1.atTime(12, 45)),
                            stamp(AttendanceType.OFF_WORK, D1.atTime(18, 0))),
                    null, null, null, BreakPolicy.KR);
            assertThat(under.get(0).breakMinutes()).isEqualTo(45);
            assertThat(under.get(0).recognizedBreakMinutes()).isEqualTo(60);
            assertThat(under.get(0).workMinutes()).isEqualTo(480);

            //90분 기록(법정 초과) → 90 인정, 근무 450
            List<DailyAttendance> over = assembler.assemble(List.of(D1), Map.of(D1, day), Map.of(),
                    List.of(stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                            breakStart(D1.atTime(12, 0)), breakEnd(D1.atTime(13, 30)),
                            stamp(AttendanceType.OFF_WORK, D1.atTime(18, 0))),
                    null, null, null, BreakPolicy.KR);
            assertThat(over.get(0).breakMinutes()).isEqualTo(90);
            assertThat(over.get(0).recognizedBreakMinutes()).isEqualTo(90);
            assertThat(over.get(0).workMinutes()).isEqualTo(450);

            //출근만(퇴근 미확정) → 인정 휴게 null
            List<DailyAttendance> open = assembler.assemble(List.of(D1), Map.of(D1, day), Map.of(),
                    List.of(stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0))),
                    null, null, null, BreakPolicy.KR);
            assertThat(open.get(0).recognizedBreakMinutes()).isNull();
        }

        @Test
        @DisplayName("CALC-07: 개인 기본값(10:00~19:00) — 오버라이드 없는 날은 개인값 기준 산출")
        void personalDefaultsApplied() {
            List<DailyAttendance> days = assembler.assemble(
                    List.of(D1), Map.of(), Map.of(),
                    List.of(
                            stamp(AttendanceType.GO_TO_WORK, D1.atTime(10, 0)),
                            stamp(AttendanceType.OFF_WORK, D1.atTime(19, 0))),
                    LocalTime.of(10, 0), LocalTime.of(19, 0), null, BreakPolicy.KR);

            assertThat(days.get(0).scheduleStart()).isEqualTo("10:00");
            assertThat(days.get(0).scheduleEnd()).isEqualTo("19:00");
            assertThat(days.get(0).statutoryBreakMinutes()).isEqualTo(60);
            assertThat(days.get(0).workMinutes()).isEqualTo(480);
        }

        @Test
        @DisplayName("CALC-08: 미출근 일자는 break/work 모두 null(법정휴게만 표시)")
        void absentDayNulls() {
            List<DailyAttendance> days = assemble(List.of());

            assertThat(days.get(0).breakMinutes()).isNull();
            assertThat(days.get(0).workMinutes()).isNull();
            assertThat(days.get(0).statutoryBreakMinutes()).isEqualTo(60);
        }

        @Test
        @DisplayName("X7: 조퇴로 체류 3h < 법정휴게 기준 — max(0, …) 클램프로 음수 없음")
        void earlyLeaveClampsToZero() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    stamp(AttendanceType.EARLY_DEPARTURE, D1.atTime(9, 30))));

            //체류 30분 − max(60, 0) → 클램프 0
            assertThat(days.get(0).workMinutes()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("BREAK 페어링(PAIR — §4 규칙)")
    class Pairing {

        @Test
        @DisplayName("PAIR-01: 다중 휴식은 전부 합산(30+40=70)")
        void multipleBreaksSummed() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    breakStart(D1.atTime(10, 0)),
                    breakEnd(D1.atTime(10, 30)),
                    breakStart(D1.atTime(15, 0)),
                    breakEnd(D1.atTime(15, 40)),
                    stamp(AttendanceType.OFF_WORK, D1.atTime(18, 0))));

            assertThat(days.get(0).breakMinutes()).isEqualTo(70);
        }

        @Test
        @DisplayName("PAIR-02: 미종료 휴식은 퇴근까지 휴식으로 간주(부풀리기 방지)")
        void unfinishedBreakCountsToOut() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    breakStart(D1.atTime(16, 0)),
                    stamp(AttendanceType.OFF_WORK, D1.atTime(18, 0))));

            assertThat(days.get(0).breakMinutes()).isEqualTo(120);
            //540 − max(60, 120) = 420
            assertThat(days.get(0).workMinutes()).isEqualTo(420);
        }

        @Test
        @DisplayName("PAIR-03: 시작 없는 종료는 무시(방어 규칙)")
        void endWithoutStartIgnored() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    breakEnd(D1.atTime(12, 0)),
                    stamp(AttendanceType.OFF_WORK, D1.atTime(18, 0))));

            assertThat(days.get(0).breakMinutes()).isEqualTo(0);
            assertThat(days.get(0).workMinutes()).isEqualTo(480);
        }

        @Test
        @DisplayName("PAIR-04: 창 밖(재출근 덮어쓰기로 버려진 구간)의 휴식은 미포함")
        void breaksOutsideWindowExcluded() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(9, 0)),
                    breakStart(D1.atTime(10, 0)),
                    breakEnd(D1.atTime(10, 30)),
                    stamp(AttendanceType.OFF_WORK, D1.atTime(12, 0)),
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(13, 0)),   //재출근 — 창은 13:00~18:00
                    stamp(AttendanceType.OFF_WORK, D1.atTime(18, 0))));

            assertThat(days.get(0).stampIn()).isEqualTo("13:00");
            assertThat(days.get(0).breakMinutes()).isEqualTo(0);
        }

        @Test
        @DisplayName("PAIR-05: 자정 넘김 휴식(23:50→00:20)은 그 근무일의 30분")
        void breakAcrossMidnight() {
            List<DailyAttendance> days = assemble(List.of(
                    stamp(AttendanceType.GO_TO_WORK, D1.atTime(15, 0)),
                    breakStart(D1.atTime(23, 50)),
                    breakEnd(D2.atTime(0, 20)),
                    stamp(AttendanceType.OFF_WORK, D2.atTime(1, 10))));

            assertThat(days.get(0).breakMinutes()).isEqualTo(30);
            //체류 610 − max(60, 30) = 550
            assertThat(days.get(0).workMinutes()).isEqualTo(550);
        }
    }

    @Nested
    @DisplayName("공휴일 명칭(HOL-01)")
    class HolidayName {

        @Test
        @DisplayName("HOL-01: 공휴일은 holidayName 동봉, 개인 휴일(schedule.holiday)은 null")
        void holidayNameSupplied() {
            WorkSchedule personalHoliday = new WorkSchedule(1L, 1L, D2, null, null, true);
            List<DailyAttendance> days = assembler.assemble(
                    List.of(D1, D2, D3),
                    Map.of(D2, personalHoliday),
                    Map.of(D1, "삼일절"),
                    List.of(),
                    null, null, null, BreakPolicy.KR);

            assertThat(days.get(0).holiday()).isTrue();
            assertThat(days.get(0).holidayName()).isEqualTo("삼일절");
            assertThat(days.get(1).holiday()).isTrue();
            assertThat(days.get(1).holidayName()).isNull();
            assertThat(days.get(2).holiday()).isFalse();
            assertThat(days.get(2).holidayName()).isNull();
        }
    }

}
