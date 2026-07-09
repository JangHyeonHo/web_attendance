package com.attendance.pro.user;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.user.MemberDtos.MemberCreateRequest;
import com.attendance.pro.user.MemberDtos.MemberCreateResponse;
import com.attendance.pro.user.MemberDtos.MemberResponse;

/**
 * 테넌트 멤버 관리 서비스(TENANT_ADMIN 등록제).
 * tenantId는 항상 세션에서 취득해 명시 전달받는다.
 * 초기 비밀번호는 서버 생성(SecureRandom) — 응답에 1회만 평문 반환하고 어디에도 보관/로그하지 않는다.
 */
@Service
public class MemberService {

    /** 초기 비밀번호 길이(PASSWORD_PATTERN 충족 12자) */
    private static final int INITIAL_PASSWORD_LENGTH = 12;
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGIT = "23456789";
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL = LOWER + UPPER + DIGIT + SPECIAL;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public MemberService(UserMapper userMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 멤버 목록(자기 테넌트만).
     */
    public List<MemberResponse> list(long tenantId) {
        return userMapper.findByTenant(tenantId).stream()
                .map(MemberResponse::from)
                .toList();
    }

    /**
     * 멤버 등록. 항상 MEMBER/ACTIVE로 생성한다(관리자 지정은 role 변경 API로만).
     * 테넌트 내 이메일 중복시 409.
     */
    @Transactional
    public MemberCreateResponse create(long tenantId, MemberCreateRequest request) {
        if (userMapper.existsByEmail(tenantId, request.email())) {
            throw ApiException.conflict("EMAIL_DUPLICATED", "member.email.duplicated");
        }
        String initialPassword = generateInitialPassword();
        UserCreate create = new UserCreate(tenantId, request.email(),
                passwordEncoder.encode(initialPassword), request.name(), request.departCd(),
                Role.MEMBER, UserStatus.ACTIVE);
        userMapper.insert(create);
        return new MemberCreateResponse(create.getUserId(), create.getEmail(), create.getName(),
                create.getDepartCd(), Role.MEMBER, UserStatus.ACTIVE, initialPassword);
    }

    /**
     * 멤버 상태 변경(ACTIVE/DISABLED — PENDING 지정은 400).
     * 타 테넌트 userId는 404(존재 비노출), 마지막 활성 TENANT_ADMIN 비활성은 409.
     */
    @Transactional
    public MemberResponse updateStatus(long tenantId, long userId, UserStatus status) {
        if (status == UserStatus.PENDING) {
            throw ApiException.badRequest("MEMBER_STATUS_INVALID", "member.status.invalid");
        }
        User target = requireManageableMember(tenantId, userId);
        if (status == UserStatus.DISABLED && target.role() == Role.TENANT_ADMIN
                && target.status() == UserStatus.ACTIVE) {
            guardLastTenantAdmin(tenantId);
        }
        userMapper.updateStatus(tenantId, userId, status);
        return MemberResponse.from(userMapper.findById(tenantId, userId));
    }

    /**
     * 멤버 역할 변경(TENANT_ADMIN/MEMBER — SYSTEM_ADMIN 지정은 400).
     * 마지막 활성 TENANT_ADMIN 강등은 409.
     */
    @Transactional
    public MemberResponse updateRole(long tenantId, long userId, Role role) {
        if (role == Role.SYSTEM_ADMIN) {
            throw ApiException.badRequest("MEMBER_ROLE_INVALID", "member.role.invalid");
        }
        User target = requireManageableMember(tenantId, userId);
        if (role == Role.MEMBER && target.role() == Role.TENANT_ADMIN
                && target.status() == UserStatus.ACTIVE) {
            guardLastTenantAdmin(tenantId);
        }
        userMapper.updateRole(tenantId, userId, role);
        return MemberResponse.from(userMapper.findById(tenantId, userId));
    }

    /**
     * 테넌트 생성시 최초 TENANT_ADMIN 발급(TenantService용 — UserMapper 접근을 user 패키지에 유지).
     */
    @Transactional
    public InitialAdmin registerInitialAdmin(long tenantId, String email, String name) {
        String initialPassword = generateInitialPassword();
        UserCreate create = new UserCreate(tenantId, email,
                passwordEncoder.encode(initialPassword), name, null,
                Role.TENANT_ADMIN, UserStatus.ACTIVE);
        userMapper.insert(create);
        return new InitialAdmin(create.getUserId(), initialPassword);
    }

    /** 최초 관리자 발급 결과(초기 비밀번호는 이 값을 응답에 1회 실은 뒤 폐기). */
    public record InitialAdmin(long userId, String initialPassword) {
    }

    /**
     * 조작 대상 멤버 로드. 타 테넌트 userId와 마찬가지로 SYSTEM_ADMIN 계정도 404
     * — V4 이관으로 DEFAULT 테넌트에 운영사 계정과 고객사 관리자가 공존할 수 있는데,
     * TENANT_ADMIN이 운영사 계정을 비활성/강등해 플랫폼 관리자를 잠그는 경로를 차단한다(존재 비노출).
     */
    private User requireManageableMember(long tenantId, long userId) {
        User target = userMapper.findById(tenantId, userId);
        if (target == null || target.role() == Role.SYSTEM_ADMIN) {
            throw ApiException.notFound("MEMBER_NOT_FOUND", "member.not-found");
        }
        return target;
    }

    /**
     * 마지막 활성 TENANT_ADMIN 보호 — 트랜잭션 내 FOR UPDATE 카운트가 1 이하면 409.
     */
    private void guardLastTenantAdmin(long tenantId) {
        if (userMapper.countActiveTenantAdmins(tenantId) <= 1) {
            throw ApiException.conflict("LAST_TENANT_ADMIN", "member.last-admin");
        }
    }

    /**
     * PASSWORD_PATTERN(3종 이상 조합)을 항상 충족하는 랜덤 초기 비밀번호를 생성한다.
     */
    private String generateInitialPassword() {
        char[] chars = new char[INITIAL_PASSWORD_LENGTH];
        //4종 문자군을 최소 1자씩 보장
        chars[0] = LOWER.charAt(random.nextInt(LOWER.length()));
        chars[1] = UPPER.charAt(random.nextInt(UPPER.length()));
        chars[2] = DIGIT.charAt(random.nextInt(DIGIT.length()));
        chars[3] = SPECIAL.charAt(random.nextInt(SPECIAL.length()));
        for (int i = 4; i < chars.length; i++) {
            chars[i] = ALL.charAt(random.nextInt(ALL.length()));
        }
        //보장 문자의 위치 고정을 피하기 위해 셔플
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

}
