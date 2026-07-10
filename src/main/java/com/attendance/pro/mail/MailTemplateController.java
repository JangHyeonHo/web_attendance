package com.attendance.pro.mail;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.mail.MailTemplateDtos.MailTemplatePreviewRequest;
import com.attendance.pro.mail.MailTemplateDtos.MailTemplatePreviewResponse;
import com.attendance.pro.mail.MailTemplateDtos.MailTemplateResponse;
import com.attendance.pro.mail.MailTemplateDtos.MailTemplateUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 메일 템플릿 관리 API (SYSTEM_ADMIN 전용 — 글로벌 제품 자산, RoleInterceptor {@code /api/v1/admin/**}).
 * 행 집합은 시드 6행 고정 — 생성/삭제 API는 두지 않는다(purpose는 코드 enum이 단일 출처).
 */
@Tag(name = "MailTemplate", description = "api.mail-template.tag")
@RestController
@RequestMapping("/api/v1/admin/mail-templates")
public class MailTemplateController {

    private final MailTemplateService mailTemplateService;

    public MailTemplateController(MailTemplateService mailTemplateService) {
        this.mailTemplateService = mailTemplateService;
    }

    @Operation(summary = "api.mail-template.list.summary")
    @GetMapping
    public List<MailTemplateResponse> list() {
        return mailTemplateService.list();
    }

    @Operation(summary = "api.mail-template.update.summary", description = "api.mail-template.update.description")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "api.mail-template.update.400"),
            @ApiResponse(responseCode = "404", description = "api.mail-template.update.404")
    })
    @PutMapping("/{purpose}/{lang}")
    public MailTemplateResponse update(@PathVariable("purpose") String purpose,
            @PathVariable("lang") String lang,
            @Valid @RequestBody MailTemplateUpdateRequest request) {
        return mailTemplateService.update(purpose, lang, request.subject(), request.body());
    }

    @Operation(summary = "api.mail-template.preview.summary", description = "api.mail-template.preview.description")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "api.mail-template.update.400")
    })
    @PostMapping("/preview")
    public MailTemplatePreviewResponse preview(@Valid @RequestBody MailTemplatePreviewRequest request) {
        return mailTemplateService.preview(request.purpose(), request.lang(),
                request.subject(), request.body());
    }

}
