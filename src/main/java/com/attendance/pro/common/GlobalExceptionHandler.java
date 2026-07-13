package com.attendance.pro.common;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.attendance.pro.audit.AuditEvent;
import com.attendance.pro.audit.AuditService;
import com.attendance.pro.auth.SessionUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * API 전역 예외 처리.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Messages messages;
    private final AuditService auditService;

    public GlobalExceptionHandler(Messages messages, AuditService auditService) {
        this.messages = messages;
        this.auditService = auditService;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        //예외가 담고 있는 메시지 키를 요청 로케일로 해석한다
        return ResponseEntity.status(e.getStatus())
                .body(ErrorResponse.of(e.getCode(), messages.get(e.getMessageKey(), e.getArgs())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldErrorDetail> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_INPUT", messages.get("error.invalid-input"), fieldErrors));
    }

    /**
     * Spring MVC 프로토콜/요청 오류(지원하지 않는 메소드 405·미디어타입 415/406·잘못된 본문 400·
     * 필수 파라미터 누락 400·핸들러/리소스 없음 404 등)는 각자의 4xx 상태로 그대로 반환한다.
     * 이 계열은 서버 결함이 아니므로 <b>감사(APP_ERROR)에 남기지 않는다</b> — 비인증 요청으로도
     * 유발 가능해, 500으로 처리하면 누구나 audit_log를 무한정 부풀릴 수 있기 때문(감사 폭주 방지).
     * 대부분의 Spring 내장 예외는 상태코드를 보유한 {@link ErrorResponse}를 구현하므로 인터페이스로 일괄 처리.
     */
    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class,
            ServletRequestBindingException.class,
            HttpMessageNotReadableException.class,
            NoHandlerFoundException.class,
            NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleSpringMvcError(Exception e) {
        //이 계열은 대부분 org.springframework.web.ErrorResponse를 구현해 정확한 4xx 상태코드를 보유한다.
        HttpStatusCode status = (e instanceof org.springframework.web.ErrorResponse er)
                ? er.getStatusCode() : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ErrorResponse.of("REQUEST_REJECTED", messages.get("error.invalid-input")));
    }

    /** 경로/쿼리 파라미터 타입 불일치(ErrorResponse 미구현 계열) — 400, 감사 미기록. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_INPUT", messages.get("error.invalid-input")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("unexpected error", e);
        //감사: 처리되지 않은 에러(진짜 500 — 서버 결함) 기록 — 세션이 있으면 행위자 식별
        Long tenantId = null;
        Long userId = null;
        String actorEmail = null;
        HttpSession session = request.getSession(false);
        if (session != null
                && session.getAttribute(SessionUser.SESSION_KEY) instanceof SessionUser user) {
            tenantId = user.tenantId();
            userId = user.userId();
            actorEmail = user.email();
        }
        auditService.record(AuditEvent.APP_ERROR, tenantId, userId, actorEmail,
                e.getClass().getSimpleName() + ": " + e.getMessage(), request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", messages.get("error.internal")));
    }

}
