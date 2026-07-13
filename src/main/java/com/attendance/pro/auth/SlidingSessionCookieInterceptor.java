package com.attendance.pro.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 세션 쿠키(JSESSIONID) 슬라이딩 갱신.
 *
 * 서블릿 컨테이너는 max-age 영속 쿠키를 <b>세션 생성 시 한 번만</b> Set-Cookie로 내려주므로,
 * 서버 세션은 요청마다 슬라이딩되는데 쿠키는 로그인 시점 기준 절대 만료가 된다(매일 쓰는 유저도
 * max-age 경과 시 재로그인 유발). 이 인터셉터가 <b>인증된 요청마다</b> 같은 세션 ID로 쿠키를
 * 동일 max-age로 재발급해 쿠키도 서버 세션과 함께 슬라이딩시킨다.
 *
 * 속성은 컨테이너와 동일한 {@code server.servlet.session.cookie.*} 값을 읽어 단일 출처를 유지한다
 * (운영 프로파일의 {@code secure=true}도 그대로 반영). 세션이 없거나 로그인 스냅샷이 없는 요청은
 * 건너뛴다(로그아웃·세션 회수 직후 새로 만들어진 언어-전용 세션 포함).
 */
@Component
public class SlidingSessionCookieInterceptor implements HandlerInterceptor {

    private final String cookieName;
    private final String cookiePath;
    private final String sameSite;
    private final boolean httpOnly;
    private final boolean secure;
    private final Duration maxAge;

    public SlidingSessionCookieInterceptor(
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String cookieName,
            @Value("${server.servlet.session.cookie.path:/}") String cookiePath,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite,
            @Value("${server.servlet.session.cookie.http-only:true}") boolean httpOnly,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
            @Value("${server.servlet.session.cookie.max-age:1d}") Duration maxAge) {
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.sameSite = sameSite;
        this.httpOnly = httpOnly;
        this.secure = secure;
        this.maxAge = maxAge;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        //로그인 스냅샷이 실린 세션만 갱신(비로그인·언어전용 세션은 영속 쿠키 대상 아님)
        if (session == null || session.getAttribute(SessionUser.SESSION_KEY) == null) {
            return true;
        }
        ResponseCookie cookie = ResponseCookie.from(cookieName, session.getId())
                .path(cookiePath)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return true;
    }

}
