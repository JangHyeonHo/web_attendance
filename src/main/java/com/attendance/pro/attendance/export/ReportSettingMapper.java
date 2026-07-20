package com.attendance.pro.attendance.export;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** tenant_report_setting 매퍼 — 근태 보고서 결재(도장)란 표시 설정. */
@Mapper
public interface ReportSettingMapper {

    @Select("SELECT stamp_enabled FROM tenant_report_setting WHERE tenant_id = #{tenantId}")
    Boolean findStampEnabled(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO tenant_report_setting (tenant_id, stamp_enabled)
            VALUES (#{tenantId}, #{enabled})
            ON DUPLICATE KEY UPDATE stamp_enabled = #{enabled}
            """)
    int upsert(@Param("tenantId") long tenantId, @Param("enabled") boolean enabled);

    /** 가산수당 적용 여부(§56) — 행 없음이면 null(서비스에서 기본 TRUE 처리). */
    @Select("SELECT pay_premium_enabled FROM tenant_report_setting WHERE tenant_id = #{tenantId}")
    Boolean findPremiumEnabled(@Param("tenantId") long tenantId);

    /** 가산 적용 토글 저장 — stamp_enabled는 INSERT 시 DEFAULT(FALSE), 갱신 시 보존. */
    @Insert("""
            INSERT INTO tenant_report_setting (tenant_id, pay_premium_enabled)
            VALUES (#{tenantId}, #{enabled})
            ON DUPLICATE KEY UPDATE pay_premium_enabled = #{enabled}
            """)
    int upsertPremium(@Param("tenantId") long tenantId, @Param("enabled") boolean enabled);
}
