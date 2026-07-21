package com.attendance.pro.attendance;

import java.time.LocalTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.common.ApiException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 회사(테넌트) 신규 멤버 기본 스케줄 — 관리자 전용({@code /api/v1/tenant/**} → TENANT_ADMIN/HR_ADMIN).
 * 멤버 등록 시 이 템플릿이 그 멤버의 정기 스케줄로 복제된다(스케줄 단일화).
 */
@Tag(name = "TenantDefaultSchedule", description = "api.tenant-default-schedule.tag")
@RestController
@RequestMapping("/api/v1/tenant/default-schedule")
public class TenantDefaultScheduleController {

    private final ScheduleAdminService scheduleAdminService;

    public TenantDefaultScheduleController(ScheduleAdminService scheduleAdminService) {
        this.scheduleAdminService = scheduleAdminService;
    }

    @Operation(summary = "api.tenant-default-schedule.get")
    @GetMapping
    public List<Day> get(@LoginUser SessionUser user) {
        return scheduleAdminService.tenantDefault(user.tenantId()).stream()
                .map(d -> new Day(d.dayOfWeek(), d.off(), hhmm(d.startTime()), hhmm(d.endTime()),
                        d.crossesMidnight()))
                .toList();
    }

    @Operation(summary = "api.tenant-default-schedule.update")
    @PutMapping
    public List<Day> update(@LoginUser SessionUser user, @RequestBody List<Day> days) {
        if (days == null || days.size() != 7) {
            throw ApiException.badRequest("DEFAULT_SCHEDULE_DAYS", "schedule.default.days");
        }
        List<TenantDefaultScheduleMapper.DefaultDay> rows = days.stream().map(this::toRow).toList();
        scheduleAdminService.saveTenantDefault(user.tenantId(), rows);
        return get(user);
    }

    private TenantDefaultScheduleMapper.DefaultDay toRow(Day d) {
        if (d.dayOfWeek() < 1 || d.dayOfWeek() > 7) {
            throw ApiException.badRequest("DEFAULT_SCHEDULE_DOW", "schedule.default.dow");
        }
        if (d.off()) {
            return new TenantDefaultScheduleMapper.DefaultDay(d.dayOfWeek(), true, null, null, false);
        }
        LocalTime start = parse(d.start());
        LocalTime end = parse(d.end());
        if (start == null || end == null) {
            throw ApiException.badRequest("DEFAULT_SCHEDULE_TIME", "schedule.default.time");
        }
        boolean crosses = d.crossesMidnight();
        //야간 교대가 아니면 종업이 시업 뒤여야 한다(자정 넘김은 crossesMidnight로만 표현)
        if (!crosses && !end.isAfter(start)) {
            throw ApiException.badRequest("DEFAULT_SCHEDULE_ORDER", "schedule.default.order");
        }
        return new TenantDefaultScheduleMapper.DefaultDay(d.dayOfWeek(), false, start, end, crosses);
    }

    private static LocalTime parse(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(time.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.badRequest("DEFAULT_SCHEDULE_TIME", "schedule.default.time");
        }
    }

    private static String hhmm(LocalTime t) {
        return t == null ? null : t.toString().substring(0, 5);
    }

    /** 요일별 기본 스케줄 1행(월~일). start/end는 "HH:mm", 휴무면 off=true. */
    public record Day(int dayOfWeek, boolean off, String start, String end, boolean crossesMidnight) {
    }
}
