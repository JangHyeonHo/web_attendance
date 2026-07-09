package com.attendance.pro.tenant;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * tenant_profile 테이블 매퍼(1:1, create-or-replace).
 * business_reg_no / contact_phone 컬럼은 "v1:" 텍스트 암호문을 그대로 보관한다.
 */
@Mapper
public interface TenantProfileMapper {

    @Select("""
            SELECT tenant_id, country,
                   business_reg_no AS business_reg_no_enc,
                   ceo_name, address, contact_name, contact_email,
                   contact_phone AS contact_phone_enc,
                   created_at, updated_at
            FROM tenant_profile
            WHERE tenant_id = #{tenantId}
            """)
    TenantProfile findById(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO tenant_profile
                (tenant_id, country, business_reg_no, ceo_name, address, contact_name, contact_email, contact_phone)
            VALUES
                (#{tenantId}, #{country}, #{businessRegNoEnc}, #{ceoName}, #{address},
                 #{contactName}, #{contactEmail}, #{contactPhoneEnc})
            ON DUPLICATE KEY UPDATE
                country = #{country},
                business_reg_no = #{businessRegNoEnc},
                ceo_name = #{ceoName},
                address = #{address},
                contact_name = #{contactName},
                contact_email = #{contactEmail},
                contact_phone = #{contactPhoneEnc}
            """)
    int upsert(@Param("tenantId") long tenantId,
            @Param("country") String country,
            @Param("businessRegNoEnc") String businessRegNoEnc,
            @Param("ceoName") String ceoName,
            @Param("address") String address,
            @Param("contactName") String contactName,
            @Param("contactEmail") String contactEmail,
            @Param("contactPhoneEnc") String contactPhoneEnc);

}
