package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.attendance.ScheduleMapper.RotaCell;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;

/**
 * 관리자 근무 스케줄(월 로타 = 일자 오버라이드) 쓰기/조회 서비스(#13).
 * 저장은 해당 월을 통째로 대체(delete + insert) — 지정한 날짜만 오버라이드로 남고 나머지는 기본 스케줄 폴백.
 * 야간 교대(crossesMidnight)·휴무(off)를 셀 단위로 표현한다.
 */
@Service
public class ScheduleAdminService {

    /** 반복 패턴 주기 상한(격주·3교대 순환 등 실무 범위 방어) */
    private static final int MAX_CYCLE_WEEKS = 8;

    private final ScheduleMapper scheduleMapper;
    private final SchedulePatternMapper patternMapper;
    private final UserMapper userMapper;
    private final TenantDefaultScheduleMapper tenantDefaultScheduleMapper;

    public ScheduleAdminService(ScheduleMapper scheduleMapper, SchedulePatternMapper patternMapper,
            UserMapper userMapper, TenantDefaultScheduleMapper tenantDefaultScheduleMapper) {
        this.scheduleMapper = scheduleMapper;
        this.patternMapper = patternMapper;
        this.userMapper = userMapper;
        this.tenantDefaultScheduleMapper = tenantDefaultScheduleMapper;
    }

    /**
     * 멤버 등록 시: 회사 기본 스케줄 템플릿을 그 멤버의 정기 스케줄(패턴)로 복제(스케줄 단일화).
     * 이미 패턴이 있거나 템플릿이 없으면 아무것도 하지 않는다(멱등·방어).
     */
    @Transactional
    public void initFromTenantDefault(long tenantId, long userId) {
        if (patternMapper.findByUser(tenantId, userId) != null) {
            return;
        }
        List<TenantDefaultScheduleMapper.DefaultDay> template = tenantDefaultScheduleMapper.findByTenant(tenantId);
        if (template.isEmpty()) {
            return;
        }
        SchedulePatternMapper.PatternInsert pi =
                new SchedulePatternMapper.PatternInsert(tenantId, userId, 1, LocalDate.of(2024, 1, 1));
        patternMapper.insertPattern(pi);
        long patternId = pi.getPatternId();
        List<SchedulePatternSlot> slots = template.stream()
                .map(d -> new SchedulePatternSlot(patternId, 0, d.dayOfWeek(), d.off(),
                        d.startTime(), d.endTime(), d.crossesMidnight()))
                .toList();
        patternMapper.insertSlots(patternId, slots);
    }

    /** 회사 기본 스케줄 조회(회사설정 편집용) — 없으면 빈 목록. */
    @Transactional(readOnly = true)
    public List<TenantDefaultScheduleMapper.DefaultDay> tenantDefault(long tenantId) {
        return tenantDefaultScheduleMapper.findByTenant(tenantId);
    }

    /** 회사 기본 스케줄 저장(교체) — 7행(월~일)으로 재작성. */
    @Transactional
    public void saveTenantDefault(long tenantId, List<TenantDefaultScheduleMapper.DefaultDay> days) {
        tenantDefaultScheduleMapper.deleteByTenant(tenantId);
        tenantDefaultScheduleMapper.insertDays(tenantId, days);
    }

    /** 새 테넌트 기본 스케줄 시드 — 월~금 09:00~18:00 근무, 토·일 휴무. */
    @Transactional
    public void seedTenantDefault(long tenantId) {
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(18, 0);
        List<TenantDefaultScheduleMapper.DefaultDay> days = new java.util.ArrayList<>();
        for (int dow = 1; dow <= 7; dow++) {
            boolean off = dow >= 6; //토(6)·일(7) 휴무
            days.add(new TenantDefaultScheduleMapper.DefaultDay(dow, off,
                    off ? null : start, off ? null : end, false));
        }
        saveTenantDefault(tenantId, days);
    }

    /** 그 달의 일자 오버라이드(로타 셀) 조회 — 편집기 초기 로드용. */
    @Transactional(readOnly = true)
    public List<WorkSchedule> monthRota(long tenantId, long userId, int year, int month) {
        requireMember(tenantId, userId);
        YearMonth ym = validMonth(year, month);
        return scheduleMapper.findBetween(tenantId, userId, ym.atDay(1), ym.plusMonths(1).atDay(1));
    }

    /**
     * 그 달의 <b>실효 스케줄</b> — 우선순위(오버라이드 > 반복 패턴 > 개인 기본값)를 적용해 날짜별 결과 + 출처(#13).
     * 통합 스케줄 화면의 달력이 "패턴이 적용된 실제 모습"을 보여주고 예외만 덮어쓰게 한다.
     */
    @Transactional(readOnly = true)
    public List<EffectiveDay> effectiveMonth(long tenantId, long userId, int year, int month) {
        requireMember(tenantId, userId);
        YearMonth ym = validMonth(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.plusMonths(1).atDay(1);

        Map<LocalDate, WorkSchedule> overrides = new java.util.HashMap<>();
        for (WorkSchedule s : scheduleMapper.findBetween(tenantId, userId, from, to)) {
            overrides.put(s.workDate(), s);
        }
        SchedulePattern pattern = patternMapper.findByUser(tenantId, userId);
        SchedulePatternResolver resolver = pattern == null ? null
                : new SchedulePatternResolver(pattern, patternMapper.findSlots(pattern.patternId()));
        WorkDefaults defaults = scheduleMapper.findWorkDefaults(tenantId, userId);
        LocalTime dStart = defaults != null && defaults.start() != null
                ? defaults.start() : MonthlyAttendanceAssembler.DEFAULT_START;
        LocalTime dEnd = defaults != null && defaults.end() != null
                ? defaults.end() : MonthlyAttendanceAssembler.DEFAULT_END;
        String workDays = defaults == null ? null : defaults.workDays();

        List<EffectiveDay> result = new java.util.ArrayList<>();
        for (LocalDate day = from; day.isBefore(to); day = day.plusDays(1)) {
            WorkSchedule ov = overrides.get(day);
            if (ov != null) {
                result.add(EffectiveDay.of(day, "OVERRIDE", ov.off(), ov.startTime(), ov.endTime(),
                        ov.crossesMidnight()));
                continue;
            }
            WorkSchedule pj = resolver == null ? null : resolver.resolve(day);
            if (pj != null) {
                result.add(EffectiveDay.of(day, "PATTERN", pj.off(), pj.startTime(), pj.endTime(),
                        pj.crossesMidnight()));
                continue;
            }
            //개인 기본값: 근무 요일이면 기본 시각, 아니면 휴무
            boolean off = !WorkDefaults.worksOn(workDays, day.getDayOfWeek());
            result.add(off
                    ? EffectiveDay.of(day, "DEFAULT", true, null, null, false)
                    : EffectiveDay.of(day, "DEFAULT", false, dStart, dEnd, false));
        }
        return result;
    }

    /**
     * 특정 날짜·시각에 그 멤버가 근무 중인지(#6) — 실효 스케줄(오버라이드 &gt; 반복 패턴 &gt; 개인 기본)로 판정.
     * 휴무면 false, 근무면 그 시각이 근무창(야간 교대는 자정 넘김 처리) 안에 드는지로 판정.
     * "특정 날짜의 특정 시간에 누가 근무 중인가" 검색에 쓰인다.
     */
    @Transactional(readOnly = true)
    public boolean isWorkingAt(long tenantId, long userId, LocalDate date, LocalTime time) {
        List<WorkSchedule> overrides = scheduleMapper.findBetween(tenantId, userId, date, date.plusDays(1));
        if (!overrides.isEmpty()) {
            WorkSchedule ov = overrides.get(0);
            return !ov.off() && within(ov.startTime(), ov.endTime(), ov.crossesMidnight(), time);
        }
        SchedulePattern pattern = patternMapper.findByUser(tenantId, userId);
        if (pattern != null) {
            SchedulePatternResolver resolver =
                    new SchedulePatternResolver(pattern, patternMapper.findSlots(pattern.patternId()));
            WorkSchedule pj = resolver.resolve(date);
            if (pj != null) {
                return !pj.off() && within(pj.startTime(), pj.endTime(), pj.crossesMidnight(), time);
            }
        }
        WorkDefaults defaults = scheduleMapper.findWorkDefaults(tenantId, userId);
        LocalTime dStart = defaults != null && defaults.start() != null
                ? defaults.start() : MonthlyAttendanceAssembler.DEFAULT_START;
        LocalTime dEnd = defaults != null && defaults.end() != null
                ? defaults.end() : MonthlyAttendanceAssembler.DEFAULT_END;
        String workDays = defaults == null ? null : defaults.workDays();
        if (!WorkDefaults.worksOn(workDays, date.getDayOfWeek())) {
            return false;
        }
        return within(dStart, dEnd, false, time);
    }

    /** 그 시각이 근무창 [start,end) 안인가. 야간 교대(자정 넘김)는 start 이후이거나 end 이전이면 근무 중. */
    private boolean within(LocalTime start, LocalTime end, boolean crossesMidnight, LocalTime time) {
        if (start == null || end == null) {
            return false;
        }
        if (crossesMidnight) {
            return !time.isBefore(start) || time.isBefore(end);
        }
        return !time.isBefore(start) && time.isBefore(end);
    }

    /** 월 로타 저장 — 그 달을 통째로 대체. cells는 오버라이드할 날짜만(빈 목록이면 그 달 오버라이드 전부 해제). */
    @Transactional
    public void saveMonthRota(long tenantId, long userId, RotaSaveRequest req) {
        requireMember(tenantId, userId);
        YearMonth ym = validMonth(req.year(), req.month());
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.plusMonths(1).atDay(1);
        List<RotaCell> cells = (req.cells() == null ? List.<RotaCellRequest>of() : req.cells()).stream()
                .map(c -> toCell(c, ym))
                .toList();
        scheduleMapper.deleteRotaInRange(tenantId, userId, from, to);
        if (!cells.isEmpty()) {
            scheduleMapper.upsertRota(tenantId, userId, cells);
        }
    }

    private RotaCell toCell(RotaCellRequest c, YearMonth ym) {
        if (c.date() == null || !YearMonth.from(c.date()).equals(ym)) {
            throw ApiException.badRequest("SCHEDULE_CELL_RANGE", "schedule.cell.range");
        }
        if (c.off()) {
            //휴무 셀 — 시각 없음
            return new RotaCell(c.date(), null, null, false, true, false);
        }
        LocalTime start = c.start();
        LocalTime end = c.end();
        if (start == null || end == null) {
            throw ApiException.badRequest("SCHEDULE_CELL_TIME", "schedule.cell.time");
        }
        boolean crosses = c.crossesMidnight();
        //야간 교대가 아니면 종업이 시업 뒤여야 한다(자정 넘김은 crossesMidnight로만 표현)
        if (!crosses && !end.isAfter(start)) {
            throw ApiException.badRequest("SCHEDULE_CELL_ORDER", "schedule.cell.order");
        }
        //야간 교대인데 종업>시업이면 자정 안 넘김 — 모순
        if (crosses && end.isAfter(start)) {
            throw ApiException.badRequest("SCHEDULE_CELL_ORDER", "schedule.cell.order");
        }
        return new RotaCell(c.date(), start, end, crosses, false, false);
    }

    // ===== 반복 패턴(#13) =====

    /** 사람의 활성 반복 패턴 조회 — 없으면 null(기본 스케줄 사용). */
    @Transactional(readOnly = true)
    public PatternResponse pattern(long tenantId, long userId) {
        requireMember(tenantId, userId);
        SchedulePattern p = patternMapper.findByUser(tenantId, userId);
        if (p == null) {
            return null;
        }
        List<PatternSlotDto> slots = patternMapper.findSlots(p.patternId()).stream()
                .map(s -> new PatternSlotDto(s.weekIndex(), s.dayOfWeek(), s.off(),
                        s.startTime(), s.endTime(), s.crossesMidnight()))
                .toList();
        return new PatternResponse(p.cycleWeeks(), slots);
    }

    /** 반복 패턴 저장(교체) — 사람당 1개. 슬롯 검증 후 기존 삭제→삽입. anchor는 이번 주 월요일. */
    @Transactional
    public void savePattern(long tenantId, long userId, PatternSaveRequest req) {
        requireMember(tenantId, userId);
        int cycle = req.cycleWeeks();
        if (cycle < 1 || cycle > MAX_CYCLE_WEEKS) {
            throw ApiException.badRequest("SCHEDULE_CYCLE_RANGE", "schedule.cycle.range");
        }
        List<PatternSlotDto> in = req.slots() == null ? List.of() : req.slots();
        //유효 슬롯만 저장(휴무 or 근무). 잘못된 시각/범위는 거부.
        LocalDate anchor = mondayOf(LocalDate.now());
        patternMapper.deleteByUser(tenantId, userId);
        if (in.isEmpty()) {
            return; //빈 저장 = 패턴 제거
        }
        var insert = new SchedulePatternMapper.PatternInsert(tenantId, userId, cycle, anchor);
        patternMapper.insertPattern(insert);
        long patternId = insert.getPatternId();
        List<SchedulePatternSlot> slots = in.stream()
                .map(s -> toSlot(patternId, s, cycle))
                .toList();
        if (!slots.isEmpty()) {
            patternMapper.insertSlots(patternId, slots);
        }
    }

    /** 반복 패턴 삭제. */
    @Transactional
    public void clearPattern(long tenantId, long userId) {
        requireMember(tenantId, userId);
        patternMapper.deleteByUser(tenantId, userId);
    }

    private SchedulePatternSlot toSlot(long patternId, PatternSlotDto s, int cycle) {
        if (s.weekIndex() < 0 || s.weekIndex() >= cycle || s.dayOfWeek() < 1 || s.dayOfWeek() > 7) {
            throw ApiException.badRequest("SCHEDULE_SLOT_RANGE", "schedule.cell.range");
        }
        if (s.off()) {
            return new SchedulePatternSlot(patternId, s.weekIndex(), s.dayOfWeek(), true, null, null, false);
        }
        if (s.start() == null || s.end() == null) {
            throw ApiException.badRequest("SCHEDULE_CELL_TIME", "schedule.cell.time");
        }
        boolean crosses = s.crossesMidnight();
        if (!crosses && !s.end().isAfter(s.start())) {
            throw ApiException.badRequest("SCHEDULE_CELL_ORDER", "schedule.cell.order");
        }
        if (crosses && s.end().isAfter(s.start())) {
            throw ApiException.badRequest("SCHEDULE_CELL_ORDER", "schedule.cell.order");
        }
        return new SchedulePatternSlot(patternId, s.weekIndex(), s.dayOfWeek(), false,
                s.start(), s.end(), crosses);
    }

    private static LocalDate mondayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - 1L);
    }

    private User requireMember(long tenantId, long userId) {
        User user = userMapper.findById(tenantId, userId);
        if (user == null) {
            throw ApiException.notFound("MEMBER_NOT_FOUND", "member.not-found");
        }
        return user;
    }

    private static YearMonth validMonth(int year, int month) {
        if (month < 1 || month > 12 || year < 1970 || year > 9998) {
            throw ApiException.badRequest("INVALID_MONTH", "attendance.month.invalid");
        }
        return YearMonth.of(year, month);
    }

    /** 로타 저장 요청 — 그 달 셀 전체 대체. */
    public record RotaSaveRequest(int year, int month, List<RotaCellRequest> cells) {
    }

    /** 로타 셀 한 칸 — off면 휴무(시각 무시), 아니면 start/end(+crossesMidnight 야간교대). */
    public record RotaCellRequest(LocalDate date, boolean off, LocalTime start, LocalTime end,
            boolean crossesMidnight) {
    }

    /** 반복 패턴 응답 — 주기 + 슬롯. */
    public record PatternResponse(int cycleWeeks, List<PatternSlotDto> slots) {
    }

    /** 반복 패턴 저장 요청 — 주기 + 슬롯(빈 목록이면 패턴 제거). */
    public record PatternSaveRequest(int cycleWeeks, List<PatternSlotDto> slots) {
    }

    /** 패턴 슬롯 DTO — (주차, 요일1..7) → 휴무/근무(시각·야간). */
    public record PatternSlotDto(int weekIndex, int dayOfWeek, boolean off,
            LocalTime start, LocalTime end, boolean crossesMidnight) {
    }

    /** 실효 스케줄 한 날 — source: OVERRIDE(로타)/PATTERN(반복)/DEFAULT(개인 기본). */
    public record EffectiveDay(LocalDate date, String source, boolean off,
            LocalTime start, LocalTime end, boolean crossesMidnight) {

        static EffectiveDay of(LocalDate date, String source, boolean off,
                LocalTime start, LocalTime end, boolean crossesMidnight) {
            return new EffectiveDay(date, source, off, start, end, crossesMidnight);
        }
    }
}
