package com.attendance.pro.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.common.Messages;
import com.attendance.pro.user.UserDtos.SignupRequest;
import com.attendance.pro.user.UserDtos.UserResponse;

/**
 * 회원 관리 서비스(가입).
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final Messages messages;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, Messages messages) {
        this.userMapper = userMapper;
        this.messages = messages;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 회원가입. 이메일 중복시 409.
     */
    public UserResponse signup(SignupRequest request) {
        if (userMapper.existsByEmail(request.email())) {
            throw ApiException.conflict("EMAIL_DUPLICATED", messages.get("user.email.duplicated"));
        }
        UserCreate create = new UserCreate(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.departCd(),
                false);
        userMapper.insert(create);
        return new UserResponse(create.getUserId(), create.getEmail(), create.getName(), create.getDepartCd(), false);
    }

}
