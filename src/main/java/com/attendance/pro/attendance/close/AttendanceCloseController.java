package com.attendance.pro.attendance.close;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.attendance.close.AttendanceCloseDtos.CloseRequest;
import com.attendance.pro.attendance.close.AttendanceCloseDtos.CloseStatusResponse;
import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 근태 마감 — 멤버 셀프 서비스({@code /api/v1/attendance/**} → MEMBER 포함).
 * 본인 월 마감을 신청·취소하고 상태를 조회한다. 승인은 관리자 컨트롤러(TenantAttendanceCloseController).
 */
@Tag(name = "AttendanceClose", description = "api.attendance-close.tag")
@RestController
@RequestMapping("/api/v1/attendance/close")
public class AttendanceCloseController {

    private final AttendanceCloseService service;

    public AttendanceCloseController(AttendanceCloseService service) {
        this.service = service;
    }

    @Operation(summary = "api.attendance-close.status")
    @GetMapping("/status")
    public CloseStatusResponse status(@LoginUser SessionUser user,
            @RequestParam("year") int year, @RequestParam("month") int month) {
        return service.status(user.tenantId(), user.userId(), year, month);
    }

    @Operation(summary = "api.attendance-close.request")
    @PostMapping
    public CloseStatusResponse request(@LoginUser SessionUser user,
            @Valid @RequestBody CloseRequest request) {
        return service.request(user.tenantId(), user.userId(), request.year(), request.month());
    }

    @Operation(summary = "api.attendance-close.cancel")
    @DeleteMapping
    public CloseStatusResponse cancel(@LoginUser SessionUser user,
            @RequestParam("year") int year, @RequestParam("month") int month) {
        return service.cancel(user.tenantId(), user.userId(), year, month);
    }
}
