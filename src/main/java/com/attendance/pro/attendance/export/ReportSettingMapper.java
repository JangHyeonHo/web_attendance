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
}
