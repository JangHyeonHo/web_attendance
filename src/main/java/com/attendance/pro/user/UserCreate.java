package com.attendance.pro.user;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 회원 등록용 파라미터.
 * MyBatis가 자동 생성 키(userId)를 되돌려 넣을 수 있도록 record가 아닌 가변 클래스로 둔다.
 * 기본 근무 시각은 서비스에서 09:00/18:00을 채운다(DB DEFAULT에 맡기지 않고 응답 조립에 즉시 사용).
 */
public class UserCreate {

    private Long userId;
    private final long tenantId;
    private final String email;
    private final String passwordHash;
    private final String name;
    private final String departCd;
    private final LocalTime defaultWorkStart;
    private final LocalTime defaultWorkEnd;
    private final Role role;
    private final UserStatus status;
    /** 입사일(선택) — null이면 매퍼가 CURDATE()로 채운다(#11). */
    private final LocalDate hireDate;

    public UserCreate(long tenantId, String email, String passwordHash, String name, String departCd,
            LocalTime defaultWorkStart, LocalTime defaultWorkEnd, Role role, UserStatus status,
            LocalDate hireDate) {
        this.tenantId = tenantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.departCd = departCd;
        this.defaultWorkStart = defaultWorkStart;
        this.defaultWorkEnd = defaultWorkEnd;
        this.role = role;
        this.status = status;
        this.hireDate = hireDate;
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

    public LocalTime getDefaultWorkStart() {
        return defaultWorkStart;
    }

    public LocalTime getDefaultWorkEnd() {
        return defaultWorkEnd;
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

}
