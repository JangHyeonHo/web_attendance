package com.attendance.pro.common;

import org.springframework.http.HttpStatus;

/**
 * 서비스 로직에서 발생시키는 API 예외.
 * 메시지는 하드코딩 문자열 대신 <b>메시지 키</b>로 보관하며,
 * {@link GlobalExceptionHandler}가 요청 로케일로 해석하여 {@link ErrorResponse}로 변환한다.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final transient Object[] args;

    public ApiException(HttpStatus status, String code, String messageKey, Object... args) {
        super(messageKey);
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.args = args;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }

    public static ApiException badRequest(String code, String messageKey, Object... args) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, messageKey, args);
    }

    public static ApiException unauthorized() {
        return unauthorized("error.unauthorized");
    }

    public static ApiException unauthorized(String messageKey) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", messageKey);
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "error.forbidden");
    }

    public static ApiException notFound(String code, String messageKey, Object... args) {
        return new ApiException(HttpStatus.NOT_FOUND, code, messageKey, args);
    }

    public static ApiException conflict(String code, String messageKey, Object... args) {
        return new ApiException(HttpStatus.CONFLICT, code, messageKey, args);
    }

}
