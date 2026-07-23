package com.attendance.pro.setting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.setting.UiThemeDtos.UiThemeResponse;
import com.attendance.pro.setting.UiThemeDtos.UiThemeUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 시스템 전역 UI 테마 설정 API (SYSTEM_ADMIN 전용 — RoleInterceptor /api/v1/admin/** 규칙).
 * 확정 테마의 배포는 navigation 응답이 담당 — 이 API는 A005 설정 화면 전용이다.
 */
@Tag(name = "AdminUiTheme", description = "api.admin-ui-theme.tag")
@RestController
@RequestMapping("/api/v1/admin/ui-theme")
public class AdminUiThemeController {

    private final UiThemeService uiThemeService;

    public AdminUiThemeController(UiThemeService uiThemeService) {
        this.uiThemeService = uiThemeService;
    }

    @Operation(summary = "api.admin-ui-theme.get.summary", description = "api.admin-ui-theme.get.description")
    @GetMapping
    public UiThemeResponse get() {
        return new UiThemeResponse(uiThemeService.setting(), uiThemeService.resolved());
    }

    @Operation(summary = "api.admin-ui-theme.update.summary", description = "api.admin-ui-theme.update.description")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "api.admin-ui-theme.update.400")
    })
    @PutMapping
    public UiThemeResponse update(@Valid @RequestBody UiThemeUpdateRequest request) {
        uiThemeService.update(request.theme());
        return new UiThemeResponse(uiThemeService.setting(), uiThemeService.resolved());
    }

}
