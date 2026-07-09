package com.attendance.pro.tenant;

/**
 * 테넌트 등록용 파라미터.
 * MyBatis가 자동 생성 키(tenantId)를 되돌려 넣을 수 있도록 record가 아닌 가변 클래스로 둔다.
 */
public class TenantCreate {

    private Long tenantId;
    private final String tenantCode;
    private final String name;

    public TenantCreate(String tenantCode, String name) {
        this.tenantCode = tenantCode;
        this.name = name;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public String getName() {
        return name;
    }

}
