package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.attendance.ScheduleAdminService.EffectiveDay;
import com.attendance.pro.attendance.ScheduleAdminService.PatternResponse;
import com.attendance.pro.attendance.ScheduleAdminService.PatternSaveRequest;
import com.attendance.pro.attendance.ScheduleAdminService.RotaSaveRequest;
import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 관리자 근무 스케줄(월 로타) API — /tenant/** 화이트리스트(인사관리자+총관리자, #13).
 * tenantId는 세션에서. 사람별 월 로타(일자 오버라이드 — 야간교대·휴무 포함)를 조회/저장한다.
 */
@Tag(name = "Schedule (admin)")
@RestController
@RequestMapping("/api/v1/tenant/schedule")
public class TenantScheduleController {

    private final ScheduleAdminService scheduleAdminService;

    public TenantScheduleController(ScheduleAdminService scheduleAdminService) {
        this.scheduleAdminService = scheduleAdminService;
    }

    @Operation(summary = "api.schedule.rota.get")
    @GetMapping("/{userId}/rota")
    public List<RotaCellResponse> rota(@LoginUser SessionUser user, @PathVariable("userId") long userId,
            @RequestParam("year") int year, @RequestParam("month") int month) {
        return scheduleAdminService.monthRota(user.tenantId(), userId, year, month).stream()
                .map(RotaCellResponse::of).toList();
    }

    @Operation(summary = "api.schedule.rota.save")
    @PutMapping("/{userId}/rota")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveRota(@LoginUser SessionUser user, @PathVariable("userId") long userId,
            @Valid @RequestBody RotaSaveRequest request) {
        scheduleAdminService.saveMonthRota(user.tenantId(), userId, request);
    }

    @Operation(summary = "api.schedule.effective.get")
    @GetMapping("/{userId}/effective")
    public List<EffectiveDay> effective(@LoginUser SessionUser user, @PathVariable("userId") long userId,
            @RequestParam("year") int year, @RequestParam("month") int month) {
        //달력이 패턴 적용 결과를 그대로 보여주도록 실효 스케줄 반환(#13)
        return scheduleAdminService.effectiveMonth(user.tenantId(), userId, year, month);
    }

    @Operation(summary = "api.schedule.pattern.get")
    @GetMapping("/{userId}/pattern")
    public PatternResponse pattern(@LoginUser SessionUser user, @PathVariable("userId") long userId) {
        return scheduleAdminService.pattern(user.tenantId(), userId);
    }

    @Operation(summary = "api.schedule.pattern.save")
    @PutMapping("/{userId}/pattern")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void savePattern(@LoginUser SessionUser user, @PathVariable("userId") long userId,
            @Valid @RequestBody PatternSaveRequest request) {
        scheduleAdminService.savePattern(user.tenantId(), userId, request);
    }

    /** 로타 셀 응답 — off면 휴무, 아니면 start/end(+crossesMidnight). */
    public record RotaCellResponse(LocalDate date, boolean off, LocalTime start, LocalTime end,
            boolean crossesMidnight, boolean holiday) {

        public static RotaCellResponse of(WorkSchedule s) {
            return new RotaCellResponse(s.workDate(), s.off(), s.startTime(), s.endTime(),
                    s.crossesMidnight(), s.holiday());
        }
    }
}
