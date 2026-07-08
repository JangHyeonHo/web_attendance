package com.attendance.pro.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.attendance.pro.attendance.AttendanceDtos.DailyAttendance;

/**
 * 월별 출결 조립(스케쥴 x 스탬프 페어링) 테스트.
 */
class MonthlyAttendanceAssemblerTest {

    private final MonthlyAttendanceAssembler assembler = new MonthlyAttendanceAssembler();
    private final AtomicLong idSeq = new AtomicLong(1);

    private static final LocalDate D1 = LocalDate.of(2026, 7, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 7, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 7, 3);

    private AttendanceStamp stamp(AttendanceType type, LocalDateTime at) {
        return new AttendanceStamp(idSeq.getAndIncrement(), 1L, type.code(), 0, at);
    }

    private List<DailyAttendance> assemble(List<AttendanceStamp> stamps) {
        return assembler.assemble(List.of(D1, D2, D3), Map.of(), Set.of(), stamps);
    }

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
    @DisplayName("휴일: 공휴일/개인휴일은 스케쥴과 스탬프 모두 공란")
    void holidays() {
        WorkSchedule personalHoliday = new WorkSchedule(1L, 1L, D2, null, null, true);
        List<DailyAttendance> days = assembler.assemble(
                List.of(D1, D2, D3),
                Map.of(D2, personalHoliday),
                Set.of(D1),
                List.of(stamp(AttendanceType.GO_TO_WORK, D3.atTime(9, 0))));

        assertThat(days.get(0).holiday()).isTrue();
        assertThat(days.get(0).scheduleStart()).isNull();
        assertThat(days.get(1).holiday()).isTrue();
        assertThat(days.get(2).holiday()).isFalse();
        assertThat(days.get(2).stampIn()).isEqualTo("09:00");
    }

    @Test
    @DisplayName("스케쥴 오버라이드: 등록된 일자는 해당 시업/종업 시각이 표시된다")
    void scheduleOverride() {
        WorkSchedule override = new WorkSchedule(1L, 1L, D1, LocalTime.of(10, 30), LocalTime.of(19, 30), false);
        List<DailyAttendance> days = assembler.assemble(
                List.of(D1, D2), Map.of(D1, override), Set.of(), List.of());

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
        });
    }

}
