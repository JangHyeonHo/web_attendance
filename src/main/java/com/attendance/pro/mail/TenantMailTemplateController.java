package com.attendance.pro.mail;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.mail.MailTemplateDtos.MailTemplatePreviewRequest;
import com.attendance.pro.mail.MailTemplateDtos.MailTemplatePreviewResponse;
import com.attendance.pro.mail.MailTemplateDtos.MailTemplateUpdateRequest;
import com.attendance.pro.mail.MailTemplateDtos.TenantMailTemplateResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 회사(테넌트)별 메일 템플릿 API — TENANT_ADMIN 전용(RoleInterceptor /api/v1/tenant/**).
 * 기본 템플릿(전역)은 그대로 제공되고, 자기 회사 문구만 오버라이드/되돌리기 한다.
 * tenantId는 항상 세션에서만 취득한다.
 */
@Tag(name = "TenantMailTemplate", description = "api.tenant-mail-template.tag")
@RestController
@RequestMapping("/api/v1/tenant/mail-templates")
public class TenantMailTemplateController {

    private final MailTemplateService mailTemplateService;

    public TenantMailTemplateController(MailTemplateService mailTemplateService) {
        this.mailTemplateService = mailTemplateService;
    }

    @Operation(summary = "api.tenant-mail-template.list.summary",
            description = "api.tenant-mail-template.list.description")
    @GetMapping
    public List<TenantMailTemplateResponse> list(@LoginUser SessionUser user) {
        return mailTemplateService.listEffective(user.tenantId());
    }

    @Operation(summary = "api.tenant-mail-template.update.summary",
            description = "api.tenant-mail-template.update.description")
    @PutMapping("/{purpose}/{lang}")
    public TenantMailTemplateResponse update(@LoginUser SessionUser user,
            @PathVariable String purpose, @PathVariable String lang,
            @Valid @RequestBody MailTemplateUpdateRequest request) {
        return mailTemplateService.updateOverride(user.tenantId(), purpose, lang,
                request.subject(), request.body());
    }

    @Operation(summary = "api.tenant-mail-template.revert.summary",
            description = "api.tenant-mail-template.revert.description")
    @DeleteMapping("/{purpose}/{lang}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revert(@LoginUser SessionUser user,
            @PathVariable String purpose, @PathVariable String lang) {
        mailTemplateService.revertOverride(user.tenantId(), purpose, lang);
    }

    @Operation(summary = "api.tenant-mail-template.preview.summary",
            description = "api.tenant-mail-template.preview.description")
    @PostMapping("/preview")
    public MailTemplatePreviewResponse preview(@LoginUser SessionUser user,
            @Valid @RequestBody MailTemplatePreviewRequest request) {
        //실제 회사명·미리보는 관리자 이름으로 치환(#11 — 저장 전 확인용, 저장하지 않는다)
        return mailTemplateService.previewForTenant(user.tenantId(), user.name(),
                request.purpose(), request.lang(), request.subject(), request.body());
    }

}
