package com.attendance.pro.attendance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.attendance.AttendanceDtos.CheckRequest;
import com.attendance.pro.attendance.AttendanceDtos.CheckResponse;
import com.attendance.pro.attendance.AttendanceDtos.ConfirmRequest;
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
@Tag(name = "Attendance", description = "출결 API (로그인 필요)")
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Operation(summary = "출결 상태 조회",
            description = "현재 출결 상태(출근 대기/출근 중/퇴근 완료 등)와 알림을 돌려준다.")
    @GetMapping("/status")
    public StatusResponse status(@LoginUser SessionUser user) {
        return attendanceService.status(user.userId());
    }

    @Operation(summary = "출결 체크",
            description = """
                    확정 전 사전 검사. 현재 상태에서 요청한 출결 타입이 가능한지 검사한다.
                    - allowed=true, requiresConfirmation=false: 바로 확정 가능
                    - allowed=true, requiresConfirmation=true: 사용자에게 덮어쓰기/재출근 확인 후 확정
                    - allowed=false: 처리 불가(메시지 표시)
                    확정 가능한 경우 token이 발급되며, 확정 요청에 동일한 데이터와 함께 보내야 한다.""")
    @PostMapping("/check")
    public CheckResponse check(@LoginUser SessionUser user, @Valid @RequestBody CheckRequest request) {
        return attendanceService.check(user.userId(), request);
    }

    @Operation(summary = "출결 확정",
            description = "체크에서 받은 토큰과 동일한 데이터로 출결 스탬프를 등록한다. 체크 시점과 데이터가 다르면 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 완료"),
            @ApiResponse(responseCode = "400", description = "토큰 불일치 또는 데이터 변조")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StampResponse confirm(@LoginUser SessionUser user, @Valid @RequestBody ConfirmRequest request) {
        return attendanceService.confirm(user.userId(), request);
    }

    @Operation(summary = "월별 출결 상세",
            description = "해당 월의 일자별 스케쥴과 출근/퇴근 시각을 돌려준다. 자정 넘긴 퇴근은 24+시(예: 25:10)로 표기.")
    @GetMapping("/monthly")
    public MonthlyResponse monthly(@LoginUser SessionUser user,
            @Parameter(description = "연도", example = "2026") @RequestParam("year") int year,
            @Parameter(description = "월(1~12)", example = "7") @RequestParam("month") int month) {
        return attendanceService.monthly(user.userId(), year, month);
    }

}
