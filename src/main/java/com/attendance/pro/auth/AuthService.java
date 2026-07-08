package com.attendance.pro.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;

/**
 * 로그인 인증 서비스.
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 이메일/비밀번호를 검증하고 세션에 담을 유저 정보를 돌려준다.
     * 인증 실패시 401 (이메일 존재 여부를 노출하지 않도록 메시지는 동일하게).
     */
    public SessionUser authenticate(String email, String rawPassword) {
        User user = userMapper.findByEmail(email);
        if (user == null || !passwordEncoder.matches(rawPassword, user.passwordHash())) {
            throw ApiException.unauthorized("존재하지 않는 이메일 혹은 비밀번호입니다.");
        }
        return new SessionUser(user.userId(), user.email(), user.name(), user.admin());
    }

}
