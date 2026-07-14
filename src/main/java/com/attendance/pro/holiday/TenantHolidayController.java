package com.attendance.pro.holiday;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.holiday.HolidayDtos.HolidayCreateRequest;
import com.attendance.pro.holiday.HolidayDtos.HolidayResponse;
import com.attendance.pro.holiday.HolidayDtos.HolidaySyncResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 공휴일 관리 API (TENANT_ADMIN 전용 — {@code /api/v1/tenant/**} 화이트리스트 자동 편입, SYSTEM_ADMIN 403).
 * tenantId는 항상 세션에서(파라미터 금지). 식별자는 날짜 자체(PK tenant_id, holiday_date와 정합).
 */
@Tag(name = "TenantHoliday", description = "api.tenant-holiday.tag")
@RestController
@RequestMapping("/api/v1/tenant/holidays")
public class TenantHolidayController {

    private final HolidayService holidayService;

    public TenantHolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @Operation(summary = "api.tenant-holiday.sync.summary", description = "api.tenant-holiday.sync.description")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "api.tenant-holiday.sync.400"),
            @ApiResponse(responseCode = "502", description = "api.tenant-holiday.sync.502")
    })
    @PostMapping("/sync")
    public HolidaySyncResponse sync(@LoginUser SessionUser user, @RequestParam("year") int year) {
        return holidayService.sync(user.tenantId(), year);
    }

    @Operation(summary = "api.tenant-holiday.list.summary")
    @GetMapping
    public List<HolidayResponse> list(@LoginUser SessionUser user, @RequestParam("year") int year) {
        return holidayService.list(user.tenantId(), year);
    }

    @Operation(summary = "api.tenant-holiday.create.summary", description = "api.tenant-holiday.create.description")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "api.tenant-holiday.create.201")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayResponse create(@LoginUser SessionUser user,
            @Valid @RequestBody HolidayCreateRequest request) {
        return holidayService.create(user.tenantId(), request);
    }

}
