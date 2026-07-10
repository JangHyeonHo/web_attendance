package com.attendance.pro.attendance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import com.attendance.pro.attendance.AttendanceDtos.CheckRequest;
import com.attendance.pro.attendance.AttendanceDtos.CheckResponse;
import com.attendance.pro.attendance.AttendanceDtos.ConfirmRequest;
import com.attendance.pro.attendance.AttendanceDtos.DailyResponse;
import com.attendance.pro.attendance.AttendanceDtos.ManualStampRequest;
import com.attendance.pro.attendance.AttendanceDtos.MonthlyResponse;
import com.attendance.pro.attendance.AttendanceDtos.StampResponse;
import com.attendance.pro.attendance.AttendanceDtos.StatusResponse;
import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 출결 API.
 */
@Tag(name = "Attendance", description = "api.attendance.tag")
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Operation(summary = "api.attendance.status.summary", description = "api.attendance.status.description")
    @GetMapping("/status")
    public StatusResponse status(@LoginUser SessionUser user) {
        return attendanceService.status(user.tenantId(), user.userId());
    }

    @Operation(summary = "api.attendance.check.summary", description = "api.attendance.check.description")
    @PostMapping("/check")
    public CheckResponse check(@LoginUser SessionUser user, @Valid @RequestBody CheckRequest request) {
        return attendanceService.check(user.tenantId(), user.userId(), request);
    }

    @Operation(summary = "api.attendance.confirm.summary", description = "api.attendance.confirm.description")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "api.attendance.confirm.201"),
            @ApiResponse(responseCode = "400", description = "api.attendance.confirm.400")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StampResponse confirm(@LoginUser SessionUser user, @Valid @RequestBody ConfirmRequest request) {
        return attendanceService.confirm(user.tenantId(), user.userId(), request);
    }

    @Operation(summary = "api.attendance.manual.summary", description = "api.attendance.manual.description")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "api.attendance.manual.201"),
            @ApiResponse(responseCode = "400", description = "api.attendance.manual.400")
    })
    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public StampResponse manual(@LoginUser SessionUser user, @Valid @RequestBody ManualStampRequest request) {
        return attendanceService.manual(user.tenantId(), user.userId(), request);
    }

    @Operation(summary = "api.attendance.manual-delete.summary",
            description = "api.attendance.manual-delete.description")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "api.attendance.manual-delete.204"),
            @ApiResponse(responseCode = "404", description = "api.attendance.manual-delete.404")
    })
    @DeleteMapping("/manual/{attendanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteManual(@LoginUser SessionUser user,
            @PathVariable("attendanceId") long attendanceId) {
        attendanceService.deleteManual(user.tenantId(), user.userId(), attendanceId);
    }

    @Operation(summary = "api.attendance.daily.summary", description = "api.attendance.daily.description")
    @GetMapping("/daily")
    public DailyResponse daily(@LoginUser SessionUser user,
            @Parameter(description = "schema.daily-attendance.date", example = "2026-07-09")
            @RequestParam("date") LocalDate date) {
        return attendanceService.daily(user.tenantId(), user.userId(), date);
    }

    @Operation(summary = "api.attendance.monthly.summary", description = "api.attendance.monthly.description")
    @GetMapping("/monthly")
    public MonthlyResponse monthly(@LoginUser SessionUser user,
            @Parameter(description = "schema.field.year", example = "2026") @RequestParam("year") int year,
            @Parameter(description = "schema.field.month", example = "7") @RequestParam("month") int month) {
        return attendanceService.monthly(user.tenantId(), user.userId(), year, month);
    }

}
