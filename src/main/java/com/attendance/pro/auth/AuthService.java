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
    //계정 부재/미설정 시에도 bcrypt를 한 번 수행해 응답 시간을 계정 존재 시와 맞추기 위한 더미 해시(타이밍 사용자열거 차단)
    private final String dummyHash;

    public AuthService(TenantMapper tenantMapper, UserMapper userMapper) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
        this.dummyHash = passwordEncoder.encode("timing-equalizer");
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
        //비밀번호 검증은 항상 bcrypt를 수행한다 — 계정이 없거나 해시가 없으면 더미 해시로 비교해
        //응답 시간을 계정 존재 시와 동일하게 만든다(계정 존재 여부 타이밍 오라클 차단).
        boolean passwordOk;
        if (user != null && user.passwordHash() != null) {
            passwordOk = passwordEncoder.matches(rawPassword, user.passwordHash());
        } else {
            passwordEncoder.matches(rawPassword, dummyHash);
            passwordOk = false;
        }
        if (user == null || user.status() != UserStatus.ACTIVE || !passwordOk) {
            throw ApiException.unauthorized("auth.login.failed");
        }
        //단일 세션 강제: 로그인마다 새 토큰을 발급·저장해 이전 기기 세션을 무효화(마지막 로그인만 유효)
        String sessionToken = java.util.UUID.randomUUID().toString();
        userMapper.updateSessionToken(tenant.tenantId(), user.userId(), sessionToken);
        //password_changed_at·session_token 스냅샷 — 이후 DB 값과 달라지면 재검증 인터셉터가 회수한다
        return new SessionUser(user.userId(), tenant.tenantId(), tenant.tenantCode(), tenant.name(),
                user.email(), user.name(), user.role(), user.passwordChangedAt(), sessionToken);
    }

    /**
     * 로그아웃 시 DB의 단일 세션 토큰을 비운다. 이후 어떤 스냅샷 토큰도 DB(null)와 불일치해
     * 잔존 세션이 다음 요청에 회수된다(로그아웃 = 전 기기 로그아웃, 재검증 SESSION_SUPERSEDED).
     */
    public void clearSession(long tenantId, long userId) {
        userMapper.updateSessionToken(tenantId, userId, null);
    }

}
