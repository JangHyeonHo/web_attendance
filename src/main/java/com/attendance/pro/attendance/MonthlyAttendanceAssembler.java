package com.attendance.pro.attendance;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.attendance.pro.attendance.AttendanceDtos.DailyAttendance;

/**
 * 월별 출결 상세 조립기.
 * 한 달치 스케쥴(달력)과 출결 스탬프(출근/퇴근/조퇴/휴식)를 대조하여 일자별 출근/퇴근 시각과
 * 실휴식·법정휴게·총 근무시간을 만든다.
 *
 * 기존 규칙(전부 불변 — work-schedule §6-1):
 * <ul>
 *   <li>같은 날 출근이 여러 번이면 마지막 출근, 퇴근이 여러 번이면 마지막 퇴근을 채용</li>
 *   <li>출근 후 다음날 48시간 이내의 퇴근/조퇴는 야근으로 보고 24를 더한 시각(예: 25:10)으로 표시</li>
 *   <li>출근 후 48시간 넘게 퇴근이 없으면 미퇴근(퇴근 공란) 처리</li>
 *   <li>출근이 연달아 찍히면 앞의 출근은 미퇴근 처리</li>
 * </ul>
 * 휴게 계산(정본 공식 — work-schedule §1-1):
 * <ul>
 *   <li>법정휴게 = BreakPolicy(스케줄 근무구간 길이) — 근무일은 항상 산출(스케줄 기반)</li>
 *   <li>실휴식 = 창(inAt~outAt 실시각) 안의 BREAK 시작→종료 짝 합산(§4 페어링).
 *       미종료 휴식은 퇴근까지 간주(부풀리기 방지), 시작 없는 종료·창 밖은 무시</li>
 *   <li>총 근무시간 = max(0, 체류 − max(법정휴게, 실휴식)). 출근·퇴근 미확정이면 null</li>
 * </ul>
 * Phase 5 변경(manual-attendance §4):
 * <ul>
 *   <li>공휴일·개인휴일·요일 휴무(dayOff)에도 스탬프를 채용한다(구현은 공란 스킵이었음).
 *       스케줄이 없으므로 법정휴게는 실체류 기반 {@code policy.requiredBreak(체류)} — 월 합계 포함</li>
 *   <li>dayOff = work_days 요일 플래그 '0' && 일자 오버라이드 없음(오버라이드가 있으면 근무일)</li>
 *   <li>manual = 그 달력 날짜에 MANUAL 스탬프 존재(테이블 마커용 — 상세는 daily API)</li>
 * </ul>
 */
public class MonthlyAttendanceAssembler {

    /** 스케쥴 미등록 일자의 기본 시업/종업 시각(유저 행 결손 등 이론적 방어선) */
    public static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    public static final LocalTime DEFAULT_END = LocalTime.of(18, 0);

    /**
     * @param monthDays    대상 월의 모든 날짜(오름차순)
     * @param schedules    일자별 스케쥴 오버라이드(work_date -> WorkSchedule)
     * @param holidays     공휴일(날짜 -> 명칭) — 판정은 containsKey(정본: holiday-plan §6, CR3-2)
     * @param stamps       전 타입 스탬프(시각 오름차순, 다음달 1일치 야근 포함 — BREAK 포함)
     * @param defaultStart 개인 기본 시업(users.default_work_start — null이면 상수 폴백)
     * @param defaultEnd   개인 기본 종업
     * @param workDays     요일별 근무 플래그(월~일 '1'=근무). null이면 전 요일 근무(방어)
     * @param breakPolicy  테넌트 소재국 법정 휴게 정책
     */
    public List<DailyAttendance> assemble(List<LocalDate> monthDays,
            Map<LocalDate, WorkSchedule> schedules,
            Map<LocalDate, String> holidays,
            List<AttendanceStamp> stamps,
            LocalTime defaultStart,
            LocalTime defaultEnd,
            String workDays,
            BreakPolicy breakPolicy) {

        LocalTime baseStart = defaultStart != null ? defaultStart : DEFAULT_START;
        LocalTime baseEnd = defaultEnd != null ? defaultEnd : DEFAULT_END;

        //수동 정정 마커(달력 날짜 단위) — 채용 여부와 무관하게 "그 날에 정정이 있었다"를 표시
        Set<LocalDate> manualDays = stamps.stream()
                .filter(AttendanceStamp::manual)
                .map(stamp -> stamp.stampedAt().toLocalDate())
                .collect(Collectors.toSet());

        //정정 사유(비고) — 날짜별로 수동 정정 스탬프의 사유를 결합(중복 제거·순서 유지)
        Map<LocalDate, java.util.LinkedHashSet<String>> noteByDay = new java.util.LinkedHashMap<>();
        for (AttendanceStamp stamp : stamps) {
            if (stamp.manual()) {
                noteByDay.computeIfAbsent(stamp.stampedAt().toLocalDate(),
                        k -> new java.util.LinkedHashSet<>()).add(reasonLabel(stamp));
            }
        }

        List<DailyAttendance> result = new ArrayList<>(monthDays.size());
        boolean attending = false;
        int cursor = 0;

        for (LocalDate day : monthDays) {
            WorkSchedule schedule = schedules.get(day);
            boolean holiday = holidays.containsKey(day) || (schedule != null && schedule.holiday());
            //요일 휴무 — 일자 오버라이드가 있으면 그날은 근무일(오버라이드 우선)
            boolean dayOff = !holiday && schedule == null
                    && !WorkDefaults.worksOn(workDays, day.getDayOfWeek());
            boolean offDuty = holiday || dayOff;

            //우선순위: work_schedule(필드 단위) > 개인 기본값 > 상수 — §3-1. 휴일·휴무는 스케줄 없음
            LocalTime resolvedStart = offDuty ? null
                    : (schedule != null && schedule.startTime() != null ? schedule.startTime() : baseStart);
            LocalTime resolvedEnd = offDuty ? null
                    : (schedule != null && schedule.endTime() != null ? schedule.endTime() : baseEnd);

            String stampIn = null;
            String stampOut = null;
            //표기 문자열 경로와 계산 경로 분리 — 표기 25:10 ↔ 계산은 실 LocalDateTime(창 구성용)
            LocalDateTime inAt = null;
            LocalDateTime outAt = null;

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
                        inAt = stamp.stampedAt();
                        cursor = i + 1;
                    } else if (type == AttendanceType.OFF_WORK || type == AttendanceType.EARLY_DEPARTURE) {
                        //출근 여부와 상관없이 같은 날 퇴근/조퇴는 퇴근 시각으로 채용(마지막 값이 남음)
                        attending = false;
                        stampOut = format(stamp.stampedAt().toLocalTime());
                        outAt = stamp.stampedAt();
                        cursor = i + 1;
                    }
                    //BREAK는 페어링 단계에서 별도 합산 — 채용 로직에는 불참여
                } else {
                    //다음날 이후의 스탬프
                    long diffHours = Duration.between(day.atStartOfDay(), stamp.stampedAt()).toHours();
                    if (!attending) {
                        //출근 상태가 아니면 이 날의 처리는 종료
                        break stampLoop;
                    }
                    if (type == AttendanceType.BREAK) {
                        //야근 중 자정 넘긴 휴식 — 퇴근 채용에는 불참여(창 기준 합산이 처리)
                        continue;
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
                    //야근(자정 넘긴 퇴근): 근무일로부터의 일수 × 24를 더한 시각으로 표시(예: 25:10, 이틀 뒤면 49:10).
                    //+24 고정이면 이틀 넘긴 경우 시각이 틀어지므로 실제 일자 차이로 계산한다.
                    LocalDateTime out = stamp.stampedAt();
                    int dayDelta = (int) (out.toLocalDate().toEpochDay() - day.toEpochDay());
                    stampOut = String.format("%02d:%02d", out.getHour() + 24 * dayDelta, out.getMinute());
                    outAt = out;
                    attending = false;
                    cursor = i + 1;
                }
            }

            //법정휴게: 근무일은 스케줄 구간 기반이라 항상 산출(X2 — 미퇴근이어도 표시),
            //휴일·휴무는 스케줄이 없으므로 출퇴근이 확정된 날만 실체류 기반으로 산출
            Integer statutory = offDuty ? null
                    : (int) breakPolicy.requiredBreak(Duration.between(resolvedStart, resolvedEnd)).toMinutes();
            //예정 근무(분) = 스케줄 구간 − 법정휴게. 근무일만(휴일·휴무는 null). 실근무와 비교용(#1)
            Integer scheduledMinutes = offDuty ? null
                    : (int) Math.max(0L,
                            Duration.between(resolvedStart, resolvedEnd).toMinutes() - statutory);
            Integer breakMinutes = null;
            Integer recognizedBreak = null;
            Integer workMinutes = null;
            if (inAt != null && outAt != null && !outAt.isBefore(inAt)) {
                long stay = Duration.between(inAt, outAt).toMinutes();
                if (offDuty) {
                    statutory = (int) breakPolicy.requiredBreak(Duration.between(inAt, outAt)).toMinutes();
                }
                long actualBreak = sumBreaks(stamps, inAt, outAt);
                breakMinutes = (int) actualBreak;
                //인정 휴게 = max(법정, 실휴식). 휴식 미기록(실0)이면 법정으로 자동 인정, 초과 기록이면 실측(§req2)
                long recognized = Math.max(statutory, actualBreak);
                recognizedBreak = (int) recognized;
                workMinutes = (int) Math.max(0L, stay - recognized);
            }
            result.add(new DailyAttendance(day, holiday, dayOff,
                    format(resolvedStart), format(resolvedEnd),
                    stampIn, stampOut, holiday ? holidays.get(day) : null,
                    scheduledMinutes, breakMinutes, statutory, recognizedBreak, workMinutes,
                    manualDays.contains(day),
                    noteByDay.containsKey(day) ? String.join(", ", noteByDay.get(day)) : null));
        }
        return result;
    }

    /** 정정 사유 표시 문자열 — 자유 텍스트가 있으면 그것을, 없으면 코드 라벨을. */
    private static String reasonLabel(AttendanceStamp stamp) {
        if (stamp.reasonText() != null && !stamp.reasonText().isBlank()) {
            return stamp.reasonText().trim();
        }
        String code = stamp.reasonCode();
        if (code == null) {
            return "정정";
        }
        return switch (code) {
            case "FORGOT" -> "미기록";
            case "DEVICE" -> "기기 오류";
            case "OFFSITE" -> "외근";
            case "OTHER" -> "기타";
            default -> code;
        };
    }

    /**
     * 창(inAt~outAt) 안의 BREAK 페어링 합산(분) — work-schedule §4.
     * 시작(0)이 열고 다음 종료(1)가 닫는다. 미종료 휴식은 outAt까지, 시작 없는 종료·창 밖은 무시.
     */
    private long sumBreaks(List<AttendanceStamp> stamps, LocalDateTime inAt, LocalDateTime outAt) {
        long total = 0;
        LocalDateTime open = null;
        for (AttendanceStamp stamp : stamps) {
            if (stamp.type() != AttendanceType.BREAK) {
                continue;
            }
            LocalDateTime at = stamp.stampedAt();
            if (at.isBefore(inAt) || at.isAfter(outAt)) {
                //창 밖(재출근 덮어쓰기로 버려진 구간 포함)은 무시 — 마지막 값 채용 규칙과 동일 귀결
                continue;
            }
            if (stamp.status() == AttendanceStamp.STATUS_ACTIVE) {
                if (open == null) {
                    open = at;
                }
            } else if (stamp.status() == AttendanceStamp.STATUS_BREAK_ENDED) {
                if (open != null) {
                    total += Duration.between(open, at).toMinutes();
                    open = null;
                }
                //시작 없는 종료는 무시(상태머신상 도달 불가 — 방어 규칙)
            }
        }
        if (open != null) {
            //미종료 휴식: 퇴근까지 휴식 간주(차감 극대화 — 근무시간 부풀리기 방지)
            total += Duration.between(open, outAt).toMinutes();
        }
        return total;
    }

    private String format(LocalTime time) {
        return time == null ? null : String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

}
