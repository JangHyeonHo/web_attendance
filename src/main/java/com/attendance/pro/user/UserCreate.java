package com.attendance.pro.user;

import java.time.LocalDate;

/**
 * 회원 등록용 파라미터.
 * MyBatis가 자동 생성 키(userId)를 되돌려 넣을 수 있도록 record가 아닌 가변 클래스로 둔다.
 * 근무 스케줄은 등록 후 회사 기본 스케줄이 정기 스케줄로 복제된다(스케줄 단일화 — users에 근무시간 없음).
 */
public class UserCreate {

    private Long userId;
    private final long tenantId;
    private final String email;
    private final String passwordHash;
    private final String name;
    private final String departCd;
    private final Role role;
    private final UserStatus status;
    /** 입사일(선택) — null이면 매퍼가 CURDATE()로 채운다(#11). */
    private final LocalDate hireDate;
    /** 월 기본급(원/円, 선택) — 급여 정산 기준. null=미입력. */
    private final Long baseMonthlySalary;

    public UserCreate(long tenantId, String email, String passwordHash, String name, String departCd,
            Role role, UserStatus status, LocalDate hireDate, Long baseMonthlySalary) {
        this.tenantId = tenantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.departCd = departCd;
        this.role = role;
        this.status = status;
        this.hireDate = hireDate;
        this.baseMonthlySalary = baseMonthlySalary;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public long getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getDepartCd() {
        return departCd;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public Long getBaseMonthlySalary() {
        return baseMonthlySalary;
    }

}
