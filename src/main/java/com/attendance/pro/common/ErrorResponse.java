package com.attendance.pro.common;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API 공통 에러 응답.
 */
@Schema(description = "에러 응답")
public record ErrorResponse(
        @Schema(description = "에러 코드", example = "INVALID_INPUT") String code,
        @Schema(description = "에러 메시지", example = "이메일 형식이 아닙니다.") String message,
        @Schema(description = "필드별 상세 에러(검증 실패시)") List<FieldErrorDetail> fieldErrors) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }

    @Schema(description = "필드 에러 상세")
    public record FieldErrorDetail(
            @Schema(description = "필드명", example = "email") String field,
            @Schema(description = "에러 메시지", example = "이메일을 입력해 주세요.") String message) {
    }

}
