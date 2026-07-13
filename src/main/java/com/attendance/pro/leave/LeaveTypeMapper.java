package com.attendance.pro.leave;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * leave_type 매퍼. 테넌트 전파 규약(tenantId 첫 @Param + 2중 조건) 준수.
 */
@Mapper
public interface LeaveTypeMapper {

    String COLS = "leave_type_id, tenant_id, code, name, paid, unit, requires_approval, "
            + "is_annual, active, sort_order, created_at, updated_at";

    @Select("SELECT " + COLS + " FROM leave_type WHERE tenant_id = #{tenantId} "
            + "ORDER BY sort_order ASC, leave_type_id ASC")
    List<LeaveType> findByTenant(@Param("tenantId") long tenantId);

    @Select("SELECT " + COLS + " FROM leave_type WHERE tenant_id = #{tenantId} AND active = TRUE "
            + "ORDER BY sort_order ASC, leave_type_id ASC")
    List<LeaveType> findActiveByTenant(@Param("tenantId") long tenantId);

    @Select("SELECT " + COLS + " FROM leave_type WHERE tenant_id = #{tenantId} "
            + "AND leave_type_id = #{leaveTypeId}")
    LeaveType findById(@Param("tenantId") long tenantId, @Param("leaveTypeId") long leaveTypeId);

    /** 자동 계산 대상 연차 종류(테넌트당 1행 시드). 없으면 null. */
    @Select("SELECT " + COLS + " FROM leave_type WHERE tenant_id = #{tenantId} "
            + "AND is_annual = TRUE ORDER BY leave_type_id ASC LIMIT 1")
    LeaveType findAnnual(@Param("tenantId") long tenantId);

    /** 신규는 항상 is_annual=FALSE(연차는 시드 전용). 중복 코드는 DuplicateKeyException → 서비스 409. */
    @Insert("""
            INSERT INTO leave_type (tenant_id, code, name, paid, unit, requires_approval,
                                    is_annual, active, sort_order)
            VALUES (#{tenantId}, #{code}, #{name}, #{paid}, #{unit}, #{requiresApproval},
                    FALSE, TRUE, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "leaveTypeId", keyColumn = "leave_type_id")
    int insert(LeaveTypeCreate type);

    /** 명칭·속성 수정. 코드·is_annual은 불변(연차 성격 전환 금지). */
    @Update("""
            UPDATE leave_type SET name = #{name}, paid = #{paid}, unit = #{unit},
                                  requires_approval = #{requiresApproval}, active = #{active},
                                  sort_order = #{sortOrder}
            WHERE tenant_id = #{tenantId} AND leave_type_id = #{leaveTypeId}
            """)
    int update(@Param("tenantId") long tenantId, @Param("leaveTypeId") long leaveTypeId,
            @Param("name") String name, @Param("paid") boolean paid,
            @Param("unit") LeaveUnit unit, @Param("requiresApproval") boolean requiresApproval,
            @Param("active") boolean active, @Param("sortOrder") int sortOrder);

    /** 등록용 파라미터(자동 생성 키 반환). */
    class LeaveTypeCreate {
        private Long leaveTypeId;
        private final long tenantId;
        private final String code;
        private final String name;
        private final boolean paid;
        private final LeaveUnit unit;
        private final boolean requiresApproval;
        private final int sortOrder;

        public LeaveTypeCreate(long tenantId, String code, String name, boolean paid,
                LeaveUnit unit, boolean requiresApproval, int sortOrder) {
            this.tenantId = tenantId;
            this.code = code;
            this.name = name;
            this.paid = paid;
            this.unit = unit;
            this.requiresApproval = requiresApproval;
            this.sortOrder = sortOrder;
        }

        public Long getLeaveTypeId() {
            return leaveTypeId;
        }

        public void setLeaveTypeId(Long leaveTypeId) {
            this.leaveTypeId = leaveTypeId;
        }

        public long getTenantId() {
            return tenantId;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }

        public boolean isPaid() {
            return paid;
        }

        public LeaveUnit getUnit() {
            return unit;
        }

        public boolean isRequiresApproval() {
            return requiresApproval;
        }

        public int getSortOrder() {
            return sortOrder;
        }
    }
}
