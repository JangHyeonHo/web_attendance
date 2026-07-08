package com.attendance.pro.auth;

import java.io.Serializable;

/**
 * HTTP 세션에 보관하는 로그인 유저 정보.
 */
public record SessionUser(long userId, String email, String name, boolean admin) implements Serializable {

    /** 세션 속성 키 */
    public static final String SESSION_KEY = "LOGIN_USER";

}
