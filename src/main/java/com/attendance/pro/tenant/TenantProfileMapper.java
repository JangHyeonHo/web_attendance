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

    /**
     * country는 tenant JOIN으로 공급(정본은 tenant.country — V7 승격, 응답 계약 불변 유지).
     */
    @Select("""
            SELECT p.tenant_id, t.country,
                   p.business_reg_no AS business_reg_no_enc,
                   p.ceo_name, p.postal_code, p.address, p.address_detail,
                   p.contact_name, p.contact_email,
                   p.contact_phone AS contact_phone_enc,
                   p.created_at, p.updated_at
            FROM tenant_profile p
            JOIN tenant t ON t.tenant_id = p.tenant_id
            WHERE p.tenant_id = #{tenantId}
            """)
    TenantProfile findById(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO tenant_profile
                (tenant_id, business_reg_no, ceo_name, postal_code, address, address_detail,
                 contact_name, contact_email, contact_phone)
            VALUES
                (#{tenantId}, #{businessRegNoEnc}, #{ceoName}, #{postalCode}, #{address}, #{addressDetail},
                 #{contactName}, #{contactEmail}, #{contactPhoneEnc})
            ON DUPLICATE KEY UPDATE
                business_reg_no = #{businessRegNoEnc},
                ceo_name = #{ceoName},
                postal_code = #{postalCode},
                address = #{address},
                address_detail = #{addressDetail},
                contact_name = #{contactName},
                contact_email = #{contactEmail},
                contact_phone = #{contactPhoneEnc}
            """)
    int upsert(@Param("tenantId") long tenantId,
            @Param("businessRegNoEnc") String businessRegNoEnc,
            @Param("ceoName") String ceoName,
            @Param("postalCode") String postalCode,
            @Param("address") String address,
            @Param("addressDetail") String addressDetail,
            @Param("contactName") String contactName,
            @Param("contactEmail") String contactEmail,
            @Param("contactPhoneEnc") String contactPhoneEnc);

}
