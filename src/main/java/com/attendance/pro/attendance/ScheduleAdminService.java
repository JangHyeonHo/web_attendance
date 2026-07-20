package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

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

    public ScheduleAdminService(ScheduleMapper scheduleMapper, SchedulePatternMapper patternMapper,
            UserMapper userMapper) {
        this.scheduleMapper = scheduleMapper;
        this.patternMapper = patternMapper;
        this.userMapper = userMapper;
    }

    /** 그 달의 일자 오버라이드(로타 셀) 조회 — 편집기 초기 로드용. */
    @Transactional(readOnly = true)
    public List<WorkSchedule> monthRota(long tenantId, long userId, int year, int month) {
        requireMember(tenantId, userId);
        YearMonth ym = validMonth(year, month);
        return scheduleMapper.findBetween(tenantId, userId, ym.atDay(1), ym.plusMonths(1).atDay(1));
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
}
