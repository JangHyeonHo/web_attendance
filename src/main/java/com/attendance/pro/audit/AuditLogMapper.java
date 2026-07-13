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

    /**
     * 전역 최근 감사 로그(SYSTEM_ADMIN 화면) — 테넌트/유저명을 LEFT JOIN(널 이벤트 보존),
     * category 선택 필터. 로그인 실패·비인증 에러 등 tenant/user가 NULL인 행도 포함한다.
     */
    @Select("""
            <script>
            SELECT a.audit_id, a.category, a.event, a.tenant_id, t.name AS tenant_name,
                   a.user_id, COALESCE(u.name, a.actor_email) AS actor, a.actor_email,
                   a.ip, a.user_agent, a.request_path, a.detail, a.created_at
            FROM audit_log a
            LEFT JOIN tenant t ON t.tenant_id = a.tenant_id
            LEFT JOIN users u ON u.user_id = a.user_id
            <where>
                <if test='category != null'>a.category = #{category}</if>
            </where>
            ORDER BY a.audit_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AuditLogView> findRecentView(@Param("category") String category, @Param("limit") int limit);

    /** 전역 감사 조회 view(테넌트·행위자 이름 포함). */
    record AuditLogView(
            long auditId,
            String category,
            String event,
            Long tenantId,
            String tenantName,
            Long userId,
            String actor,
            String actorEmail,
            String ip,
            String userAgent,
            String requestPath,
            String detail,
            java.time.LocalDateTime createdAt) {
    }
}
