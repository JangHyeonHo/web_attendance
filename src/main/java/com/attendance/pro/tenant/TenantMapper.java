package com.attendance.pro.tenant;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.attendance.pro.tenant.TenantDtos.TenantResponse;

/**
 * tenant 테이블 매퍼.
 * 테넌트 소유 테이블이 아니므로 tenantId 첫 파라미터 규약의 예외 —
 * system API는 경로의 tenantId를 명시 전달한다.
 */
@Mapper
public interface TenantMapper {

    @Select("""
            SELECT tenant_id, tenant_code, name, country, status, created_at
            FROM tenant
            WHERE tenant_code = #{tenantCode}
            """)
    Tenant findByCode(@Param("tenantCode") String tenantCode);

    @Select("""
            SELECT tenant_id, tenant_code, name, country, status, created_at
            FROM tenant
            WHERE tenant_id = #{tenantId}
            """)
    Tenant findById(@Param("tenantId") long tenantId);

    @Select("""
            SELECT t.tenant_id, t.tenant_code, t.name, t.country, t.status,
                   (SELECT COUNT(*) FROM users u
                     WHERE u.tenant_id = t.tenant_id AND u.deleted = FALSE) AS member_count,
                   t.created_at
            FROM tenant t
            ORDER BY t.tenant_id ASC
            """)
    List<TenantResponse> findAllWithMemberCount();

    @Select("""
            SELECT t.tenant_id, t.tenant_code, t.name, t.country, t.status,
                   (SELECT COUNT(*) FROM users u
                     WHERE u.tenant_id = t.tenant_id AND u.deleted = FALSE) AS member_count,
                   t.created_at
            FROM tenant t
            WHERE t.tenant_id = #{tenantId}
            """)
    TenantResponse findByIdWithMemberCount(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO tenant (tenant_code, name, country, status)
            VALUES (#{tenantCode}, #{name}, #{country}, 'ACTIVE')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "tenantId", keyColumn = "tenant_id")
    int insert(TenantCreate tenant);

    @Update("UPDATE tenant SET status = #{status} WHERE tenant_id = #{tenantId}")
    int updateStatus(@Param("tenantId") long tenantId, @Param("status") TenantStatus status);

    @Select("SELECT EXISTS(SELECT 1 FROM tenant WHERE tenant_code = #{tenantCode})")
    boolean existsByCode(@Param("tenantCode") String tenantCode);

}
