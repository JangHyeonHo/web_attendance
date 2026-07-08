package com.attendance.pro.common;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API 공통 에러 응답.
 */
@Schema(description = "schema.error-response")
public record ErrorResponse(
        @Schema(description = "schema.error-response.code", example = "INVALID_INPUT") String code,
        @Schema(description = "schema.error-response.message", example = "이메일 형식이 아닙니다.") String message,
        @Schema(description = "schema.error-response.field-errors") List<FieldErrorDetail> fieldErrors) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }

    @Schema(description = "schema.field-error")
    public record FieldErrorDetail(
            @Schema(description = "schema.field-error.field", example = "email") String field,
            @Schema(description = "schema.error-response.message", example = "이메일을 입력해 주세요.") String message) {
    }

}
