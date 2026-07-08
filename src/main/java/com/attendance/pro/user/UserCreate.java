package com.attendance.pro.user;

/**
 * 회원 등록용 파라미터.
 * MyBatis가 자동 생성 키(userId)를 되돌려 넣을 수 있도록 record가 아닌 가변 클래스로 둔다.
 */
public class UserCreate {

    private Long userId;
    private final String email;
    private final String passwordHash;
    private final String name;
    private final String departCd;
    private final boolean admin;

    public UserCreate(String email, String passwordHash, String name, String departCd, boolean admin) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.departCd = departCd;
        this.admin = admin;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public boolean isAdmin() {
        return admin;
    }

}
