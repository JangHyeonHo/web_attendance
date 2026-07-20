package com.attendance.pro.auth;

import java.util.Locale;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.auth.AuthDtos.TokenVerifyResponse;
import com.attendance.pro.common.Masking;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.tenant.TenantStatus;
import com.attendance.pro.user.MemberInviteService;
import com.attendance.pro.user.TokenPurpose;
import com.attendance.pro.user.User;
import com.attendance.pro.user.UserMapper;
import com.attendance.pro.user.UserStatus;
import com.attendance.pro.user.UserToken;
import com.attendance.pro.user.UserTokenService;

/**
 * 공개 비밀번호 설정/재설정 서비스.
 * verify/set은 테넌트 스코프를 요구하지 않는다 — 토큰 행이 (tenant_id, user_id)를 보유하므로 토큰이 곧 스코프.
 * 모든 무효 사유(부존재/만료/사용 완료/상태 불일치/삭제)는 통일 404 {@code TOKEN_INVALID}(열거 방지).
 */
@Service
public class PasswordService {

    private final UserTokenService userTokenService;
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final MemberInviteService memberInviteService;
    private final com.attendance.pro.billing.BillingService billingService;
    private final PasswordEncoder passwordEncoder;

    public PasswordService(UserTokenService userTokenService, UserMapper userMapper,
            TenantMapper tenantMapper, MemberInviteService memberInviteService,
            com.attendance.pro.billing.BillingService billingService) {
        this.userTokenService = userTokenService;
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.memberInviteService = memberInviteService;
        this.billingService = billingService;
        this.passwordEncoder = new BCryptPasswordEncoder(12); //강도 12(권장)
    }

    /**
     * 토큰 검증 — W010 표시용 정보(이름/회사명/마스킹 이메일/만료)를 돌려준다.
     */
    public TokenVerifyResponse verify(String rawToken) {
        UserToken token = userTokenService.verify(rawToken);
        User user = requireEligibleUser(token);
        Tenant tenant = requireActiveTenant(token.tenantId());
        return new TokenVerifyResponse(token.purpose(), user.name(), Masking.email(user.email()),
                tenant.name(), token.expiresAt());
    }

    /**
     * 비밀번호 설정(초대 완료/재설정 공용).
     * [Tx] 토큰 선점(used_at=NOW — 0행이면 동시 요청이 선점한 것, 통일 404)
     * → hash 교체 + INVITE는 PENDING→ACTIVE + password_changed_at=NOW(SQL 동시 세팅)
     * → 잔여 유효 토큰 전부 DELETE.
     */
    @Transactional
    public void set(String rawToken, String rawPassword) {
        UserToken token = userTokenService.verify(rawToken);
        User user = requireEligibleUser(token);
        requireActiveTenant(token.tenantId());
        //선점 먼저 — used_at IS NULL 조건부 UPDATE가 1회용의 원자 지점(동시 2요청 중 1건만 통과)
        if (userTokenService.markUsed(token.tenantId(), token.tokenHash()) == 0) {
            throw UserTokenService.invalidToken();
        }
        userMapper.updatePassword(token.tenantId(), user.userId(), passwordEncoder.encode(rawPassword));
        if (token.purpose() == TokenPurpose.INVITE) {
            userMapper.updateStatus(token.tenantId(), user.userId(), UserStatus.ACTIVE);
            //초대 활성화 = 활성 좌석 증가 → 월중 최대(과금 기준) 갱신(best-effort)
            billingService.touchSeatUsage(token.tenantId());
        }
        userTokenService.invalidateAll(token.tenantId(), user.userId());
    }

    /**
     * 재설정 요청 — 응답은 계정 존재와 무관하게 202 통일(존재 비노출).
     * 내부: status=ACTIVE 계정일 때만 발급·발송. 부존재/PENDING/DISABLED/테넌트 부존재·정지/발송 실패는
     * 전부 조용히 무시(오류 응답 자체가 존재/상태 오라클이 된다 — 실패는 로그만).
     */
    public void requestReset(String tenantCode, String email) {
        String normalizedCode = tenantCode == null ? "" : tenantCode.trim().toUpperCase(Locale.ROOT);
        Tenant tenant = tenantMapper.findByCode(normalizedCode);
        if (tenant == null || tenant.status() == TenantStatus.SUSPENDED) {
            return;
        }
        User user = userMapper.findByEmail(tenant.tenantId(), email);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            return;
        }
        //비동기 발송 — SMTP 왕복이 응답 시간에 실리면 그 자체가 계정 존재 오라클(리뷰 P3-1)
        memberInviteService.sendResetAsync(tenant.tenantId(), user.userId(), user.email(), user.name());
    }

    /**
     * 토큰 대상 유저 로드 — deleted 제외 + 용도별 기대 상태(INVITE=PENDING, RESET=ACTIVE).
     * 어긋나면 통일 404(정지·삭제 유저의 잔존 토큰 무효화 이중 방어).
     */
    private User requireEligibleUser(UserToken token) {
        User user = userMapper.findById(token.tenantId(), token.userId());
        UserStatus expected = token.purpose() == TokenPurpose.INVITE ? UserStatus.PENDING : UserStatus.ACTIVE;
        if (user == null || user.status() != expected) {
            throw UserTokenService.invalidToken();
        }
        return user;
    }

    /**
     * 테넌트 부존재/SUSPENDED면 통일 404 — 정지 회사의 잔존 토큰으로 비밀번호를 바꿔
     * 정지 해제 순간 바로 로그인하는 우회를 차단한다(리뷰 P3-3).
     */
    private Tenant requireActiveTenant(long tenantId) {
        Tenant tenant = tenantMapper.findById(tenantId);
        if (tenant == null || tenant.status() == TenantStatus.SUSPENDED) {
            throw UserTokenService.invalidToken();
        }
        return tenant;
    }

}
