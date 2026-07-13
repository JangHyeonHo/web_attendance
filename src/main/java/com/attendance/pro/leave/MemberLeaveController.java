package com.attendance.pro.leave;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.leave.LeaveDtos.LeaveApplyRequest;
import com.attendance.pro.leave.LeaveDtos.LeaveBalanceResponse;
import com.attendance.pro.leave.LeaveDtos.LeaveRequestResponse;
import com.attendance.pro.leave.LeaveDtos.LeaveTypeResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 멤버 휴가 API — 본인 잔여 조회·신청·취소. /attendance/** 화이트리스트(멤버+인사+총) 편승.
 * tenantId·userId는 항상 세션에서 취득(요청 값 불신).
 */
@Tag(name = "Leave (member)")
@RestController
@RequestMapping("/api/v1/attendance/leave")
public class MemberLeaveController {

    private final LeaveService leaveService;

    public MemberLeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @Operation(summary = "신청 가능한 휴가 종류(활성)")
    @GetMapping("/types")
    public List<LeaveTypeResponse> types(@LoginUser SessionUser user) {
        return leaveService.listActiveTypes(user.tenantId());
    }

    @Operation(summary = "본인 잔여 휴가(종류별)")
    @GetMapping("/balances")
    public List<LeaveBalanceResponse> balances(@LoginUser SessionUser user) {
        return leaveService.balances(user.tenantId(), user.userId());
    }

    @Operation(summary = "본인 휴가 신청 내역")
    @GetMapping("/requests")
    public List<LeaveRequestResponse> myRequests(@LoginUser SessionUser user) {
        return leaveService.myRequests(user.tenantId(), user.userId());
    }

    @Operation(summary = "휴가 신청")
    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestResponse apply(@LoginUser SessionUser user,
            @Valid @RequestBody LeaveApplyRequest request) {
        return leaveService.apply(user.tenantId(), user.userId(), request);
    }

    @Operation(summary = "본인 휴가 신청 취소")
    @PostMapping("/requests/{requestId}/cancel")
    public void cancel(@LoginUser SessionUser user, @PathVariable("requestId") long requestId) {
        leaveService.cancel(user.tenantId(), user.userId(), requestId);
    }
}
