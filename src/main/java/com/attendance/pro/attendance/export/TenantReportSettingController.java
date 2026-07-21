package com.attendance.pro.attendance.export;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.attendance.export.ReportSettingDtos.ReportSettingRequest;
import com.attendance.pro.attendance.export.ReportSettingDtos.ReportSettingResponse;
import com.attendance.pro.attendance.export.ReportSettingDtos.StampImageRequest;
import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 회사(테넌트) 근태 보고서 설정 관리 — 관리자 전용(경로 {@code /api/v1/tenant/**} → TENANT_ADMIN/HR_ADMIN).
 * 조회는 멤버도 필요(자기 보고서 인쇄 시 결재란 표시 판단)하므로 {@code AttendanceController}의
 * {@code /api/v1/attendance/report-setting}(전 멤버)로 노출한다.
 */
@Tag(name = "TenantReportSetting", description = "api.tenant-report-setting.tag")
@RestController
@RequestMapping("/api/v1/tenant/report-setting")
public class TenantReportSettingController {

    private final ReportSettingService reportSettingService;

    public TenantReportSettingController(ReportSettingService reportSettingService) {
        this.reportSettingService = reportSettingService;
    }

    @Operation(summary = "api.tenant-report-setting.get")
    @GetMapping
    public ReportSettingResponse get(@LoginUser SessionUser user) {
        return current(user.tenantId());
    }

    @Operation(summary = "api.tenant-report-setting.update")
    @PutMapping
    public ReportSettingResponse update(@LoginUser SessionUser user,
            @Valid @RequestBody ReportSettingRequest request) {
        reportSettingService.setStampEnabled(user.tenantId(), request.stampEnabled());
        reportSettingService.setPremiumEnabled(user.tenantId(), request.premiumEnabled());
        reportSettingService.setStampSize(user.tenantId(), request.stampSize());
        return current(user.tenantId());
    }

    @Operation(summary = "api.tenant-report-setting.stamp-image")
    @PutMapping("/stamp-image")
    public ReportSettingResponse uploadStamp(@LoginUser SessionUser user,
            @Valid @RequestBody StampImageRequest request) {
        reportSettingService.setStampImage(user.tenantId(), request.imageBase64(), request.mime());
        return current(user.tenantId());
    }

    @Operation(summary = "api.tenant-report-setting.stamp-image-delete")
    @DeleteMapping("/stamp-image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeStamp(@LoginUser SessionUser user) {
        reportSettingService.clearStampImage(user.tenantId());
    }

    private ReportSettingResponse current(long tenantId) {
        return new ReportSettingResponse(
                reportSettingService.stampEnabled(tenantId),
                reportSettingService.premiumEnabled(tenantId),
                reportSettingService.stampSize(tenantId),
                reportSettingService.stampImageDataUrl(tenantId));
    }
}
