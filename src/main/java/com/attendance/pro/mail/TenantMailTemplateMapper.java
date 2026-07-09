package com.attendance.pro.mail;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.attendance.pro.user.TokenPurpose;

/**
 * tenant_mail_template 테이블 매퍼(회사별 오버라이드 — 없으면 전역 mail_template 폴백).
 * 테넌트 전파 규약: tenantId 첫 @Param + 조건 병기.
 */
@Mapper
public interface TenantMailTemplateMapper {

    @Select("""
            SELECT tenant_id, purpose, lang, subject, body, created_at, updated_at
            FROM tenant_mail_template
            WHERE tenant_id = #{tenantId}
            ORDER BY purpose, lang
            """)
    List<TenantMailTemplate> findByTenant(@Param("tenantId") long tenantId);

    @Select("""
            SELECT tenant_id, purpose, lang, subject, body, created_at, updated_at
            FROM tenant_mail_template
            WHERE tenant_id = #{tenantId} AND purpose = #{purpose} AND lang = #{lang}
            """)
    TenantMailTemplate find(@Param("tenantId") long tenantId,
            @Param("purpose") TokenPurpose purpose, @Param("lang") String lang);

    @Insert("""
            INSERT INTO tenant_mail_template (tenant_id, purpose, lang, subject, body)
            VALUES (#{tenantId}, #{purpose}, #{lang}, #{subject}, #{body})
            ON DUPLICATE KEY UPDATE subject = #{subject}, body = #{body}
            """)
    int upsert(@Param("tenantId") long tenantId,
            @Param("purpose") TokenPurpose purpose, @Param("lang") String lang,
            @Param("subject") String subject, @Param("body") String body);

    @Delete("""
            DELETE FROM tenant_mail_template
            WHERE tenant_id = #{tenantId} AND purpose = #{purpose} AND lang = #{lang}
            """)
    int delete(@Param("tenantId") long tenantId,
            @Param("purpose") TokenPurpose purpose, @Param("lang") String lang);

}
