package com.attendance.pro.attendance;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.attendance.pro.attendance.AttendanceDtos.DailyAttendance;

/**
 * 월별 출결 상세 조립기.
 * 한 달치 스케쥴(달력)과 출결 스탬프(출근/퇴근/조퇴)를 대조하여 일자별 출근/퇴근 시각을 만든다.
 *
 * 규칙(기존 시스템의 동작을 계승):
 * <ul>
 *   <li>같은 날 출근이 여러 번이면 마지막 출근, 퇴근이 여러 번이면 마지막 퇴근을 채용</li>
 *   <li>출근 후 다음날 48시간 이내의 퇴근/조퇴는 야근으로 보고 24를 더한 시각(예: 25:10)으로 표시</li>
 *   <li>출근 후 48시간 넘게 퇴근이 없으면 미퇴근(퇴근 공란) 처리</li>
 *   <li>출근이 연달아 찍히면 앞의 출근은 미퇴근 처리</li>
 *   <li>휴일은 스케쥴/스탬프 모두 공란</li>
 * </ul>
 */
public class MonthlyAttendanceAssembler {

    /** 스케쥴 미등록 일자의 기본 시업/종업 시각 */
    public static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    public static final LocalTime DEFAULT_END = LocalTime.of(18, 0);

    /**
     * @param monthDays    대상 월의 모든 날짜(오름차순)
     * @param schedules    일자별 스케쥴 오버라이드(work_date -> WorkSchedule)
     * @param holidayDates 공휴일 집합
     * @param stamps       출근/퇴근/조퇴 스탬프(시각 오름차순, 다음달 1일치 야근 포함)
     */
    public List<DailyAttendance> assemble(List<LocalDate> monthDays,
            Map<LocalDate, WorkSchedule> schedules,
            Set<LocalDate> holidayDates,
            List<AttendanceStamp> stamps) {

        List<DailyAttendance> result = new ArrayList<>(monthDays.size());
        boolean attending = false;
        int cursor = 0;

        for (LocalDate day : monthDays) {
            WorkSchedule schedule = schedules.get(day);
            boolean holiday = holidayDates.contains(day) || (schedule != null && schedule.holiday());
            if (holiday) {
                result.add(new DailyAttendance(day, true, null, null, null, null));
                continue;
            }
            String scheduleStart = format(schedule != null && schedule.startTime() != null ? schedule.startTime() : DEFAULT_START);
            String scheduleEnd = format(schedule != null && schedule.endTime() != null ? schedule.endTime() : DEFAULT_END);

            String stampIn = null;
            String stampOut = null;

            stampLoop:
            for (int i = cursor; i < stamps.size(); i++) {
                AttendanceStamp stamp = stamps.get(i);
                LocalDate stampDay = stamp.stampedAt().toLocalDate();
                AttendanceType type = stamp.type();
                //대상 날짜보다 이전의 스탬프는 건너뜀
                if (stampDay.isBefore(day)) {
                    continue;
                }
                if (stampDay.isEqual(day)) {
                    if (type == AttendanceType.GO_TO_WORK) {
                        attending = true;
                        stampIn = format(stamp.stampedAt().toLocalTime());
                        cursor = i + 1;
                    } else if (type == AttendanceType.OFF_WORK || type == AttendanceType.EARLY_DEPARTURE) {
                        //출근 여부와 상관없이 같은 날 퇴근/조퇴는 퇴근 시각으로 채용(마지막 값이 남음)
                        attending = false;
                        stampOut = format(stamp.stampedAt().toLocalTime());
                        cursor = i + 1;
                    }
                } else {
                    //다음날 이후의 스탬프
                    long diffHours = Duration.between(day.atStartOfDay(), stamp.stampedAt()).toHours();
                    if (!attending) {
                        //출근 상태가 아니면 이 날의 처리는 종료
                        break stampLoop;
                    }
                    if (type == AttendanceType.GO_TO_WORK) {
                        //다음날 출근이 연달아 찍힘 -> 전날은 미퇴근 처리하고 다음날 처리로 넘어감
                        attending = false;
                        break stampLoop;
                    }
                    //다음날의 퇴근/조퇴
                    if (diffHours > 48) {
                        //48시간 초과는 미퇴근 처리
                        attending = false;
                        break stampLoop;
                    }
                    //야근(자정 넘긴 퇴근): 24를 더한 시각으로 표시
                    LocalDateTime out = stamp.stampedAt();
                    stampOut = String.format("%02d:%02d", out.getHour() + 24, out.getMinute());
                    attending = false;
                    cursor = i + 1;
                }
            }

            result.add(new DailyAttendance(day, false, scheduleStart, scheduleEnd, stampIn, stampOut));
        }
        return result;
    }

    private String format(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

}
