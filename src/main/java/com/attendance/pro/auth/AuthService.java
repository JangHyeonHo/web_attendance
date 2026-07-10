package com.attendance.pro.auth;

import java.util.Locale;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.tenant.TenantStatus;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;
import com.attendance.pro.user.UserStatus;

/**
 * 로그인 인증 서비스.
 */
@Service
public class AuthService {

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(TenantMapper tenantMapper, UserMapper userMapper) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 테넌트 코드/이메일/비밀번호를 검증하고 세션에 담을 유저 정보를 돌려준다.
     * 실패 사유(테넌트 부존재·정지, 이메일 부존재, 비밀번호 불일치, 비활성 계정)와 무관하게
     * 전부 동일한 401 메시지를 쓴다 — 존재 여부 비노출.
     */
    public SessionUser authenticate(String tenantCode, String email, String rawPassword) {
        //테넌트 코드는 대문자 정규화(trim + upper) 후 해석한다
        String normalizedCode = tenantCode == null ? "" : tenantCode.trim().toUpperCase(Locale.ROOT);
        Tenant tenant = tenantMapper.findByCode(normalizedCode);
        if (tenant == null || tenant.status() == TenantStatus.SUSPENDED) {
            throw ApiException.unauthorized("auth.login.failed");
        }
        User user = userMapper.findByEmail(tenant.tenantId(), email);
        if (user == null || user.status() != UserStatus.ACTIVE
                || !passwordEncoder.matches(rawPassword, user.passwordHash())) {
            throw ApiException.unauthorized("auth.login.failed");
        }
        //password_changed_at 스냅샷 — 이후 DB 값과 달라지면 재검증 인터셉터가 세션을 회수한다
        return new SessionUser(user.userId(), tenant.tenantId(), tenant.tenantCode(), tenant.name(),
                user.email(), user.name(), user.role(), user.passwordChangedAt());
    }

}
