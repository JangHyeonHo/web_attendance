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
@Tag(name = "Language", description = "다국어 텍스트 API")
@RestController
public class LanguageController {

    private final LanguageService languageService;

    public LanguageController(LanguageService languageService) {
        this.languageService = languageService;
    }

    @Operation(summary = "화면 텍스트 조회",
            description = "화면(그룹) ID와 언어에 해당하는 텍스트 맵(key → value)을 돌려준다. 로그인 불필요.")
    @GetMapping("/api/v1/i18n/{windowId}")
    public Map<String, String> texts(
            @Parameter(description = "화면(그룹) ID", example = "attendance") @PathVariable("windowId") String windowId,
            @Parameter(description = "언어(KOR/ENG)", example = "KOR") @RequestParam(name = "lang", defaultValue = "KOR") String lang) {
        return languageService.texts(windowId, lang);
    }

    @Operation(summary = "[관리자] 언어 텍스트 목록", description = "언어 마스터 전체/조건 목록을 돌려준다.")
    @GetMapping("/api/v1/admin/i18n")
    public List<EntryResponse> list(
            @Parameter(description = "화면(그룹) ID 필터") @RequestParam(name = "windowId", required = false) String windowId,
            @Parameter(description = "언어 필터(KOR/ENG)") @RequestParam(name = "lang", required = false) String lang) {
        return languageService.list(windowId, lang);
    }

    @Operation(summary = "[관리자] 언어 텍스트 등록/갱신", description = "동일 키가 있으면 값을 갱신한다.")
    @PostMapping("/api/v1/admin/i18n")
    @ResponseStatus(HttpStatus.CREATED)
    public void upsert(@Valid @RequestBody UpsertRequest request) {
        languageService.upsert(request);
    }

}
