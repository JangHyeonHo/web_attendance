package com.attendance.pro.language;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 언어 마스터 API 요청/응답 DTO 모음.
 */
public final class LanguageDtos {

    private LanguageDtos() {
    }

    @Schema(description = "schema.i18n-upsert")
    public record UpsertRequest(
            @Schema(description = "schema.field.window-id", example = "attendance")
            @NotBlank(message = "{validation.i18n.window.required}")
            @Size(max = 20)
            String windowId,

            @Schema(description = "schema.field.lang-key", example = "title")
            @NotBlank(message = "{validation.i18n.key.required}")
            @Size(max = 50)
            String langKey,

            @Schema(description = "schema.field.lang", example = "KOR")
            @NotBlank(message = "{validation.i18n.lang.required}")
            @Pattern(regexp = "KOR|ENG|JPN", message = "{validation.lang.supported}")
            String lang,

            @Schema(description = "schema.field.lang-value", example = "출결 관리")
            @NotBlank(message = "{validation.i18n.value.required}")
            @Size(max = 1000)
            String langValue) {
    }

    @Schema(description = "schema.i18n-entry")
    public record EntryResponse(
            @Schema(description = "schema.field.window-id", example = "attendance") String windowId,
            @Schema(description = "schema.field.lang-key", example = "title") String langKey,
            @Schema(description = "schema.field.lang", example = "KOR") String lang,
            @Schema(description = "schema.field.lang-value", example = "출결 관리") String langValue) {

        public static EntryResponse from(LanguageEntry entry) {
            return new EntryResponse(entry.windowId(), entry.langKey(), entry.lang(), entry.langValue());
        }
    }

}
