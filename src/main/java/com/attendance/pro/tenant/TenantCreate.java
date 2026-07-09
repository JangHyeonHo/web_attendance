package com.attendance.pro.tenant;

/**
 * 테넌트 등록용 파라미터.
 * MyBatis가 자동 생성 키(tenantId)를 되돌려 넣을 수 있도록 record가 아닌 가변 클래스로 둔다.
 */
public class TenantCreate {

    private Long tenantId;
    private final String tenantCode;
    private final String name;
    private final String country;

    public TenantCreate(String tenantCode, String name, String country) {
        this.tenantCode = tenantCode;
        this.name = name;
        this.country = country;
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

    public String getCountry() {
        return country;
    }

}
