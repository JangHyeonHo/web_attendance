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

    private final ScheduleMapper scheduleMapper;
    private final UserMapper userMapper;

    public ScheduleAdminService(ScheduleMapper scheduleMapper, UserMapper userMapper) {
        this.scheduleMapper = scheduleMapper;
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
}
