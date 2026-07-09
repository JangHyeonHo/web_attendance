package com.attendance.pro.mail;

import java.time.LocalDateTime;

import com.attendance.pro.user.TokenPurpose;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 메일 템플릿 관리 API(/api/v1/admin/mail-templates) 요청/응답 DTO 모음.
 */
public final class MailTemplateDtos {

    private MailTemplateDtos() {
    }

    @Schema(description = "schema.mail-template-response")
    public record MailTemplateResponse(
            TokenPurpose purpose, String lang, String subject, String body,
            LocalDateTime updatedAt) {

        public static MailTemplateResponse from(MailTemplate template) {
            return new MailTemplateResponse(template.purpose(), template.lang(),
                    template.subject(), template.body(), template.updatedAt());
        }
    }

    @Schema(description = "schema.mail-template-update-request")
    public record MailTemplateUpdateRequest(
            @NotBlank(message = "{validation.mail-template.subject.required}")
            @Size(max = 200, message = "{validation.mail-template.subject.size}")
            String subject,

            @NotBlank(message = "{validation.mail-template.body.required}")
            String body) {
    }

    @Schema(description = "schema.mail-template-preview-request")
    public record MailTemplatePreviewRequest(
            @NotBlank(message = "{validation.mail-template.purpose.required}") String purpose,
            @NotBlank(message = "{validation.mail-template.lang.required}") String lang,
            @NotBlank(message = "{validation.mail-template.subject.required}")
            @Size(max = 200, message = "{validation.mail-template.subject.size}")
            String subject,
            @NotBlank(message = "{validation.mail-template.body.required}") String body) {
    }

    @Schema(description = "schema.mail-template-preview-response")
    public record MailTemplatePreviewResponse(String subject, String body) {
    }

}
