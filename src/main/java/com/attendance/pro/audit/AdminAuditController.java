package com.attendance.pro.audit;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.audit.AuditDtos.AuditLogResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 감사 로그 조회 API — SYSTEM_ADMIN 전용(RoleInterceptor /api/v1/admin/** 화이트리스트).
 * 전역(모든 테넌트 + 비인증 이벤트) 최신순. category(AUTH/ERROR) 선택 필터.
 */
@Tag(name = "Audit", description = "api.audit.tag")
@RestController
@RequestMapping("/api/v1/admin/audit")
public class AdminAuditController {

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** 조회 상한 — 과도한 limit로 감사 테이블 전체를 끌어오는 것을 방지(1~MAX 클램프). */
    private static final int MAX_LIMIT = 500;

    @Operation(summary = "api.audit.list")
    @GetMapping
    public List<AuditLogResponse> list(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "limit", required = false, defaultValue = "100") int limit) {
        int clamped = Math.max(1, Math.min(limit, MAX_LIMIT));
        return auditService.recentView(category, clamped);
    }
}
