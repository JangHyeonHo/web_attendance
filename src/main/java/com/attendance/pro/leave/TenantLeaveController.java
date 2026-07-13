package com.attendance.pro.leave;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.leave.LeaveDtos.HireDateRequest;
import com.attendance.pro.leave.LeaveDtos.LeaveDecisionRequest;
import com.attendance.pro.leave.LeaveDtos.LeaveGrantRequest;
import com.attendance.pro.leave.LeaveDtos.LeaveRequestResponse;
import com.attendance.pro.leave.LeaveDtos.LeaveTypeCreateRequest;
import com.attendance.pro.leave.LeaveDtos.LeaveTypeResponse;
import com.attendance.pro.leave.LeaveDtos.LeaveTypeUpdateRequest;
import com.attendance.pro.leave.LeaveDtos.MemberLeaveDetail;
import com.attendance.pro.leave.LeaveDtos.MemberLeaveSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 관리자 휴가 API — 종류 관리·부여·결재·멤버 잔여. /tenant/** 화이트리스트(인사관리자+총관리자).
 */
@Tag(name = "Leave (admin)")
@RestController
@RequestMapping("/api/v1/tenant/leave")
public class TenantLeaveController {

    private final LeaveService leaveService;

    public TenantLeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    // ---- 종류 ----

    @Operation(summary = "api.leave.admin.types")
    @GetMapping("/types")
    public List<LeaveTypeResponse> types(@LoginUser SessionUser user) {
        return leaveService.listTypes(user.tenantId());
    }

    @Operation(summary = "api.leave.admin.create-type")
    @PostMapping("/types")
    public LeaveTypeResponse createType(@LoginUser SessionUser user,
            @Valid @RequestBody LeaveTypeCreateRequest request) {
        return leaveService.createType(user.tenantId(), request);
    }

    @Operation(summary = "api.leave.admin.update-type")
    @PutMapping("/types/{leaveTypeId}")
    public LeaveTypeResponse updateType(@LoginUser SessionUser user,
            @PathVariable("leaveTypeId") long leaveTypeId,
            @Valid @RequestBody LeaveTypeUpdateRequest request) {
        return leaveService.updateType(user.tenantId(), leaveTypeId, request);
    }

    // ---- 결재 ----

    @Operation(summary = "api.leave.admin.pending")
    @GetMapping("/requests/pending")
    public List<LeaveRequestResponse> pending(@LoginUser SessionUser user) {
        return leaveService.pendingRequests(user.tenantId());
    }

    @Operation(summary = "api.leave.admin.decide")
    @PostMapping("/requests/{requestId}/decision")
    public void decide(@LoginUser SessionUser user, @PathVariable("requestId") long requestId,
            @Valid @RequestBody LeaveDecisionRequest request) {
        leaveService.decide(user.tenantId(), user.userId(), requestId, request.approve(),
                request.note());
    }

    // ---- 부여/재계산 ----

    @Operation(summary = "api.leave.admin.grant")
    @PostMapping("/grants")
    public void grant(@LoginUser SessionUser user, @Valid @RequestBody LeaveGrantRequest request) {
        leaveService.grantManual(user.tenantId(), user.userId(), request);
    }

    @Operation(summary = "api.leave.admin.recompute")
    @PostMapping("/members/{userId}/recompute")
    public void recompute(@LoginUser SessionUser user, @PathVariable("userId") long userId) {
        leaveService.recomputeAnnual(user.tenantId(), user.userId(), userId);
    }

    @Operation(summary = "api.leave.admin.recompute-all")
    @PostMapping("/recompute")
    public Map<String, Integer> recomputeAll(@LoginUser SessionUser user) {
        return Map.of("count", leaveService.recomputeAnnualAll(user.tenantId(), user.userId()));
    }

    // ---- 멤버 잔여 ----

    @Operation(summary = "api.leave.admin.members")
    @GetMapping("/members")
    public List<MemberLeaveSummary> members(@LoginUser SessionUser user) {
        return leaveService.memberOverview(user.tenantId());
    }

    @Operation(summary = "api.leave.admin.member-detail")
    @GetMapping("/members/{userId}")
    public MemberLeaveDetail memberDetail(@LoginUser SessionUser user,
            @PathVariable("userId") long userId) {
        return leaveService.memberDetail(user.tenantId(), userId);
    }

    @Operation(summary = "api.leave.admin.hire-date")
    @PutMapping("/members/{userId}/hire-date")
    public void updateHireDate(@LoginUser SessionUser user, @PathVariable("userId") long userId,
            @Valid @RequestBody HireDateRequest request) {
        leaveService.updateHireDate(user.tenantId(), userId, request.hireDate());
    }
}
