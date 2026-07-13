package com.attendance.pro.common;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("unexpected error", e);
        //감사: 처리되지 않은 에러(500) 기록 — 세션이 있으면 행위자 식별
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
