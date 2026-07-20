package com.attendance.pro.tenant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.tenant.TenantDtos.TenantProfileRequest;
import com.attendance.pro.tenant.TenantDtos.TenantProfileResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 회사(테넌트) 자사 기업정보 자율 관리 API(#14) — 총관리자(TENANT_ADMIN) 전용.
 * 운영사가 계약(가격/좌석)을 관리한다면, 회사는 자기 사업자정보·연락처를 스스로 등록/수정한다.
 * tenantId는 세션에서만 취득한다(경로 파라미터 없음 — 타 회사 접근 불가). 서비스 로직은 운영사용과 공유.
 */
@Tag(name = "TenantProfile", description = "api.tenant-profile.tag")
@RestController
@RequestMapping("/api/v1/tenant/profile")
public class TenantProfileController {

    private final TenantProfileService tenantProfileService;

    public TenantProfileController(TenantProfileService tenantProfileService) {
        this.tenantProfileService = tenantProfileService;
    }

    //미등록이면 404가 아니라 200 빈 응답(null) — 자기 회사 정보라 미등록이 정상 흐름(빈 폼·청구서 안내)
    @Operation(summary = "api.tenant-profile.get")
    @GetMapping
    public TenantProfileResponse get(@LoginUser SessionUser user) {
        return tenantProfileService.findProfileOrNull(user.tenantId());
    }

    @Operation(summary = "api.tenant-profile.update")
    @PutMapping
    public TenantProfileResponse update(@LoginUser SessionUser user,
            @Valid @RequestBody TenantProfileRequest request) {
        return tenantProfileService.upsertProfile(user.tenantId(), request);
    }
}
