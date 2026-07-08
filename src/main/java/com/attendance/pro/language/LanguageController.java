package com.attendance.pro.language;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.language.LanguageDtos.EntryResponse;
import com.attendance.pro.language.LanguageDtos.UpsertRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 다국어 텍스트 API.
 * 조회는 공개, 등록/목록 관리는 관리자 전용(/api/v1/admin/**).
 */
@Tag(name = "Language", description = "api.language.tag")
@RestController
public class LanguageController {

    private final LanguageService languageService;

    public LanguageController(LanguageService languageService) {
        this.languageService = languageService;
    }

    @Operation(summary = "api.language.texts.summary", description = "api.language.texts.description")
    @GetMapping("/api/v1/i18n/{windowId}")
    public Map<String, String> texts(
            @Parameter(description = "schema.field.window-id", example = "attendance") @PathVariable("windowId") String windowId,
            @Parameter(description = "schema.field.lang", example = "KOR") @RequestParam(name = "lang", defaultValue = "KOR") String lang) {
        return languageService.texts(windowId, lang);
    }

    @Operation(summary = "api.language.list.summary", description = "api.language.list.description")
    @GetMapping("/api/v1/admin/i18n")
    public List<EntryResponse> list(
            @Parameter(description = "schema.field.window-id-filter") @RequestParam(name = "windowId", required = false) String windowId,
            @Parameter(description = "schema.field.lang-filter") @RequestParam(name = "lang", required = false) String lang) {
        return languageService.list(windowId, lang);
    }

    @Operation(summary = "api.language.upsert.summary", description = "api.language.upsert.description")
    @PostMapping("/api/v1/admin/i18n")
    @ResponseStatus(HttpStatus.CREATED)
    public void upsert(@Valid @RequestBody UpsertRequest request) {
        languageService.upsert(request);
    }

}
