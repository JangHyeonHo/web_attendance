package com.attendance.pro.attendance.close;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.attendance.AttendanceDtos.PayrollResponse;
import com.attendance.pro.attendance.PayrollService;
import com.attendance.pro.attendance.close.AttendanceCloseDtos.CloseDecisionRequest;
import com.attendance.pro.attendance.close.AttendanceCloseDtos.PendingCloseResponse;
import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 근태 마감 결재(관리자) — {@code /api/v1/tenant/**} → TENANT_ADMIN/HR_ADMIN.
 * 대기 목록 조회 + 승인/반려 + (검토용) 멤버별 급여 정산(참고) 조회. 승인 시 대상 월이 정정 잠금된다.
 * 급여 정산은 확정 실지급이 아니므로 멤버 본인에게 노출하지 않고 관리자만 확인한다.
 */
@Tag(name = "TenantAttendanceClose", description = "api.tenant-attendance-close.tag")
@RestController
@RequestMapping("/api/v1/tenant/attendance-close")
public class TenantAttendanceCloseController {

    private final AttendanceCloseService service;
    private final PayrollService payrollService;

    public TenantAttendanceCloseController(AttendanceCloseService service, PayrollService payrollService) {
        this.service = service;
        this.payrollService = payrollService;
    }

    @Operation(summary = "api.tenant-attendance-close.pending")
    @GetMapping("/pending")
    public List<PendingCloseResponse> pending(@LoginUser SessionUser user) {
        return service.pending(user.tenantId());
    }

    @Operation(summary = "api.tenant-attendance-close.decision")
    @PostMapping("/{closeId}/decision")
    public void decide(@LoginUser SessionUser user, @PathVariable("closeId") long closeId,
            @Valid @RequestBody CloseDecisionRequest request) {
        service.decide(user.tenantId(), user.userId(), closeId, request.approve(), request.note());
    }

    /** 멤버 급여 정산(참고) — 관리자 전용. 마감 검토 시 해당 멤버·월의 가감 명세를 확인한다. */
    @Operation(summary = "api.tenant-attendance-close.payroll")
    @GetMapping("/{userId}/payroll")
    public PayrollResponse payroll(@LoginUser SessionUser user, @PathVariable("userId") long userId,
            @RequestParam("year") int year, @RequestParam("month") int month) {
        return PayrollResponse.of(payrollService.settlement(user.tenantId(), userId, year, month));
    }
}
