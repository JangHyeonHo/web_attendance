package com.attendance.pro.setting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * UI 테마 설정 API DTO 모음.
 */
public final class UiThemeDtos {

    private UiThemeDtos() {
    }

    @Schema(description = "schema.ui-theme-request")
    public record UiThemeUpdateRequest(
            @Schema(description = "schema.ui-theme-request.theme", example = "AUTO")
            @NotBlank(message = "{validation.theme.supported}")
            String theme) {
    }

    @Schema(description = "schema.ui-theme-response")
    public record UiThemeResponse(
            @Schema(description = "schema.ui-theme-response.theme", example = "AUTO") UiTheme theme,
            @Schema(description = "schema.ui-theme-response.resolved", example = "SUMMER") UiTheme resolved) {
    }

}
