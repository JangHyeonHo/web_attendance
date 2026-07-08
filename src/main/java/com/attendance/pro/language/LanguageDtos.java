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

    @Schema(description = "언어 텍스트 등록/갱신 요청")
    public record UpsertRequest(
            @Schema(description = "화면(그룹) ID", example = "attendance")
            @NotBlank(message = "{validation.i18n.window.required}")
            @Size(max = 20)
            String windowId,

            @Schema(description = "텍스트 키", example = "title")
            @NotBlank(message = "{validation.i18n.key.required}")
            @Size(max = 50)
            String langKey,

            @Schema(description = "언어(KOR/ENG/JPN)", example = "KOR")
            @NotBlank(message = "{validation.i18n.lang.required}")
            @Pattern(regexp = "KOR|ENG|JPN", message = "{validation.lang.supported}")
            String lang,

            @Schema(description = "텍스트 값", example = "출결 관리")
            @NotBlank(message = "{validation.i18n.value.required}")
            @Size(max = 1000)
            String langValue) {
    }

    @Schema(description = "언어 텍스트 엔트리")
    public record EntryResponse(
            @Schema(description = "화면(그룹) ID", example = "attendance") String windowId,
            @Schema(description = "텍스트 키", example = "title") String langKey,
            @Schema(description = "언어", example = "KOR") String lang,
            @Schema(description = "텍스트 값", example = "출결 관리") String langValue) {

        public static EntryResponse from(LanguageEntry entry) {
            return new EntryResponse(entry.windowId(), entry.langKey(), entry.lang(), entry.langValue());
        }
    }

}
