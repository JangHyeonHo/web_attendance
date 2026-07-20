package com.attendance.pro.attendance;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import com.attendance.pro.attendance.AttendanceDtos.ManualBreakRequest;
import com.attendance.pro.attendance.AttendanceDtos.ManualStampRequest;
import com.attendance.pro.attendance.AttendanceDtos.MonthlyResponse;
import com.attendance.pro.attendance.AttendanceDtos.StampResponse;
import com.attendance.pro.attendance.AttendanceDtos.StatusResponse;
import com.attendance.pro.attendance.export.AttendanceExporters;
import com.attendance.pro.attendance.export.ExportMeta;
import com.attendance.pro.attendance.export.ReportSettingDtos.ReportSettingResponse;
import com.attendance.pro.attendance.export.ReportSettingService;
import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;

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
    private final AttendanceExporters exporters;
    private final UserMapper userMapper;
    private final ReportSettingService reportSettingService;
    private final PayrollService payrollService;

    public AttendanceController(AttendanceService attendanceService, AttendanceExporters exporters,
            UserMapper userMapper, ReportSettingService reportSettingService,
            PayrollService payrollService) {
        this.attendanceService = attendanceService;
        this.exporters = exporters;
        this.userMapper = userMapper;
        this.reportSettingService = reportSettingService;
        this.payrollService = payrollService;
    }

    @Operation(summary = "api.attendance.report-setting")
    @GetMapping("/report-setting")
    public ReportSettingResponse reportSetting(@LoginUser SessionUser user) {
        return new ReportSettingResponse(reportSettingService.stampEnabled(user.tenantId()),
                reportSettingService.premiumEnabled(user.tenantId()));
    }

    @Operation(summary = "api.attendance.payroll.summary", description = "api.attendance.payroll.description")
    @GetMapping("/payroll")
    public AttendanceDtos.PayrollResponse payroll(@LoginUser SessionUser user,
            @Parameter(description = "schema.field.year", example = "2026") @RequestParam("year") int year,
            @Parameter(description = "schema.field.month", example = "7") @RequestParam("month") int month) {
        return AttendanceDtos.PayrollResponse.of(
                payrollService.settlement(user.tenantId(), user.userId(), year, month));
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

    @Operation(summary = "api.attendance.manual-break.summary",
            description = "api.attendance.manual-break.description")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "api.attendance.manual-break.201"),
            @ApiResponse(responseCode = "400", description = "api.attendance.manual.400")
    })
    @PostMapping("/manual/break")
    @ResponseStatus(HttpStatus.CREATED)
    public StampResponse manualBreak(@LoginUser SessionUser user,
            @Valid @RequestBody ManualBreakRequest request) {
        return attendanceService.manualBreak(user.tenantId(), user.userId(), request);
    }

    @Operation(summary = "api.attendance.manual-update.summary",
            description = "api.attendance.manual-update.description")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "api.attendance.manual.400"),
            @ApiResponse(responseCode = "404", description = "api.attendance.manual-update.404")
    })
    @PutMapping("/manual/{attendanceId}")
    public StampResponse updateManual(@LoginUser SessionUser user,
            @PathVariable("attendanceId") long attendanceId,
            @Valid @RequestBody ManualStampRequest request) {
        return attendanceService.updateManual(user.tenantId(), user.userId(), attendanceId, request);
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

    @Operation(summary = "api.attendance.monthly.export.summary", description = "api.attendance.monthly.export.description")
    @GetMapping("/monthly/export")
    public ResponseEntity<byte[]> exportMonthly(@LoginUser SessionUser user,
            @Parameter(description = "schema.field.year", example = "2026") @RequestParam("year") int year,
            @Parameter(description = "schema.field.month", example = "7") @RequestParam("month") int month,
            @RequestParam(value = "lang", defaultValue = "KOR") String lang) {
        MonthlyResponse data = attendanceService.monthly(user.tenantId(), user.userId(), year, month);
        User me = userMapper.findById(user.tenantId(), user.userId());
        String department = me == null ? null : me.departCd();
        boolean stamp = reportSettingService.stampEnabled(user.tenantId());
        ExportMeta meta = new ExportMeta(user.tenantName(), user.name(), department, year, month,
                lang, LocalDate.now(), stamp);
        byte[] xlsx = exporters.forTenant(user.tenantId()).toXlsx(data, meta);
        String filename = String.format("attendance-%d-%02d.xlsx", year, month); //ASCII 파일명(인코딩 이슈 회피)
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

}
