package com.attendance.pro.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * 컨트롤러 파라미터에 세션 로그인 유저({@link SessionUser})를 주입하는 어노테이션.
 * Swagger 문서에는 파라미터로 노출하지 않는다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface LoginUser {
}
