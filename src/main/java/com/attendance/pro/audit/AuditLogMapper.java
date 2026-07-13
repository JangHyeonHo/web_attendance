package com.attendance.pro.audit;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * audit_log 매퍼. 삽입은 요청 흐름을 절대 깨지 않도록 서비스에서 예외를 삼킨다.
 * 조회는 테넌트 스코프(감사 화면 신설 시 사용).
 */
@Mapper
public interface AuditLogMapper {

    @Insert("""
            INSERT INTO audit_log (tenant_id, user_id, category, event, detail,
                                   actor_email, ip, user_agent, request_path)
            VALUES (#{tenantId}, #{userId}, #{category}, #{event}, #{detail},
                    #{actorEmail}, #{ip}, #{userAgent}, #{requestPath})
            """)
    int insert(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
            @Param("category") String category, @Param("event") String event,
            @Param("detail") String detail, @Param("actorEmail") String actorEmail,
            @Param("ip") String ip, @Param("userAgent") String userAgent,
            @Param("requestPath") String requestPath);

    /** 테넌트 최근 감사 로그(신설 감사 화면용) — 최신순 상한 조회. */
    @Select("""
            SELECT audit_id, tenant_id, user_id, category, event, detail, actor_email,
                   ip, user_agent, request_path, created_at
            FROM audit_log
            WHERE tenant_id = #{tenantId}
            ORDER BY audit_id DESC
            LIMIT #{limit}
            """)
    List<AuditLog> findRecentByTenant(@Param("tenantId") long tenantId, @Param("limit") int limit);
}
