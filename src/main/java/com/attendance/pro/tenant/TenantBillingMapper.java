package com.attendance.pro.tenant;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.attendance.pro.tenant.TenantDtos.BillingMethod;

/**
 * tenant_billing 테이블 매퍼(1:1, create-or-replace).
 * billing_method 컬럼은 TINYINT(0=INVOICE, 1=CARD) — SQL의 CASE로 enum 이름과 상호 변환한다.
 * pg_customer_key 컬럼은 "v1:" 텍스트 암호문을 그대로 보관한다.
 */
@Mapper
public interface TenantBillingMapper {

    @Select("""
            SELECT tenant_id,
                   CASE billing_method WHEN 1 THEN 'CARD' ELSE 'INVOICE' END AS billing_method,
                   billing_email,
                   pg_customer_key AS pg_customer_key_enc,
                   card_last4, card_brand, plan, per_seat_amount, free_seats, billed_from, memo,
                   created_at, updated_at
            FROM tenant_billing
            WHERE tenant_id = #{tenantId}
            """)
    TenantBilling findById(@Param("tenantId") long tenantId);

    @Insert("""
            INSERT INTO tenant_billing
                (tenant_id, billing_method, billing_email, pg_customer_key,
                 card_last4, card_brand, plan, per_seat_amount, free_seats, billed_from, memo)
            VALUES
                (#{tenantId},
                 CASE #{billingMethod} WHEN 'CARD' THEN 1 ELSE 0 END,
                 #{billingEmail}, #{pgCustomerKeyEnc},
                 #{cardLast4}, #{cardBrand}, COALESCE(#{plan}, 'BASIC'),
                 #{perSeatAmount}, #{freeSeats}, #{billedFrom}, #{memo})
            ON DUPLICATE KEY UPDATE
                billing_method = CASE #{billingMethod} WHEN 'CARD' THEN 1 ELSE 0 END,
                billing_email = #{billingEmail},
                pg_customer_key = #{pgCustomerKeyEnc},
                card_last4 = #{cardLast4},
                card_brand = #{cardBrand},
                plan = COALESCE(#{plan}, 'BASIC'),
                per_seat_amount = #{perSeatAmount},
                free_seats = #{freeSeats},
                billed_from = #{billedFrom},
                memo = #{memo}
            """)
    int upsert(@Param("tenantId") long tenantId,
            @Param("billingMethod") BillingMethod billingMethod,
            @Param("billingEmail") String billingEmail,
            @Param("pgCustomerKeyEnc") String pgCustomerKeyEnc,
            @Param("cardLast4") String cardLast4,
            @Param("cardBrand") String cardBrand,
            @Param("plan") String plan,
            @Param("perSeatAmount") int perSeatAmount,
            @Param("freeSeats") int freeSeats,
            @Param("billedFrom") LocalDate billedFrom,
            @Param("memo") String memo);

    /**
     * 회사(테넌트) 자사 결제 정보만 갱신 — 결제수단·청구 이메일·비고(#14).
     * 단가/무료좌석/요금제 등 가격 필드는 운영사 전용이라 건드리지 않는다(신규 행은 기본 가격으로 생성).
     */
    @Insert("""
            INSERT INTO tenant_billing
                (tenant_id, billing_method, billing_email, memo, plan, per_seat_amount, free_seats)
            VALUES
                (#{tenantId},
                 CASE #{billingMethod} WHEN 'CARD' THEN 1 ELSE 0 END,
                 #{billingEmail}, #{memo}, 'BASIC', 2000, 5)
            ON DUPLICATE KEY UPDATE
                billing_method = CASE #{billingMethod} WHEN 'CARD' THEN 1 ELSE 0 END,
                billing_email = #{billingEmail},
                memo = #{memo}
            """)
    int upsertProfile(@Param("tenantId") long tenantId,
            @Param("billingMethod") BillingMethod billingMethod,
            @Param("billingEmail") String billingEmail,
            @Param("memo") String memo);

}
