package com.attendance.pro.billing;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * invoice 테이블 매퍼(확정 청구서 스냅샷). 행 존재 = 그 달 확정(ISSUED).
 * 재마감 방지를 위해 INSERT는 중복 시 실패(서비스에서 사전 존재 검사 + PK 제약 이중 방어).
 */
@Mapper
public interface InvoiceMapper {

    @Insert("""
            INSERT INTO invoice
                (tenant_id, ym, max_seats, free_seats, billed_seats, seat_days, days_in_month,
                 unit_price, subtotal, vat, total)
            VALUES
                (#{tenantId}, #{ym}, #{maxSeats}, #{freeSeats}, #{billedSeats}, #{seatDays}, #{daysInMonth},
                 #{unitPrice}, #{subtotal}, #{vat}, #{total})
            """)
    int insert(@Param("tenantId") long tenantId, @Param("ym") String ym,
            @Param("maxSeats") int maxSeats, @Param("freeSeats") int freeSeats,
            @Param("billedSeats") int billedSeats, @Param("seatDays") long seatDays,
            @Param("daysInMonth") int daysInMonth, @Param("unitPrice") int unitPrice,
            @Param("subtotal") long subtotal, @Param("vat") long vat, @Param("total") long total);

    @Select("""
            SELECT tenant_id, ym, max_seats, free_seats, billed_seats, seat_days, days_in_month,
                   unit_price, subtotal, vat, total, issued_at
            FROM invoice
            WHERE tenant_id = #{tenantId} AND ym = #{ym}
            """)
    Invoice find(@Param("tenantId") long tenantId, @Param("ym") String ym);

    @Select("""
            SELECT tenant_id, ym, max_seats, free_seats, billed_seats, seat_days, days_in_month,
                   unit_price, subtotal, vat, total, issued_at
            FROM invoice
            WHERE tenant_id = #{tenantId}
            ORDER BY ym DESC
            """)
    List<Invoice> listByTenant(@Param("tenantId") long tenantId);
}
