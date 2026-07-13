package com.attendance.pro.audit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.attendance.pro.audit.AuditDtos.AuditLogResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 감사 기록 서비스. 인증 이벤트·애플리케이션 에러를 audit_log에 남긴다.
 *
 * 원칙: <b>감사 기록 실패가 본 요청을 절대 깨뜨리지 않는다</b>(예외를 삼키고 경고 로그만).
 * IP/UA/경로는 요청에서 뽑아 컬럼 길이에 맞게 잘라 저장한다.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogMapper mapper;

    public AuditService(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    /** 요청 컨텍스트(IP/UA/경로)를 포함해 기록. request는 null 허용(비HTTP 컨텍스트). */
    public void record(AuditEvent event, Long tenantId, Long userId, String actorEmail,
            String detail, HttpServletRequest request) {
        try {
            String ip = request == null ? null : request.getRemoteAddr();
            String ua = request == null ? null : request.getHeader("User-Agent");
            String path = request == null ? null : request.getRequestURI();
            mapper.insert(tenantId, userId, event.category().name(), event.name(),
                    trunc(detail, 500), trunc(actorEmail, 100), trunc(ip, 45),
                    trunc(ua, 300), trunc(path, 200));
        } catch (Exception e) {
            //감사는 부가 기능 — 실패해도 본 요청 흐름을 막지 않는다
            log.warn("audit record failed: event={} tenant={} user={}", event, tenantId, userId, e);
        }
    }

    /**
     * 전역 최근 감사 로그(SYSTEM_ADMIN). category 필터(AUTH/ERROR)는 유효값만 적용(그 외/공백은 전체).
     * limit은 1~500으로 클램프.
     */
    public List<AuditLogResponse> recentView(String category, int limit) {
        String cat = normalizeCategory(category);
        int capped = Math.max(1, Math.min(limit, 500));
        return mapper.findRecentView(cat, capped).stream().map(AuditLogResponse::of).toList();
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        for (AuditCategory c : AuditCategory.values()) {
            if (c.name().equalsIgnoreCase(category.trim())) {
                return c.name();
            }
        }
        return null; //알 수 없는 값은 전체로 취급
    }

    private static String trunc(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
