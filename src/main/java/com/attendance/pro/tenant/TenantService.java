package com.attendance.pro.tenant;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.tenant.TenantDtos.TenantCreateRequest;
import com.attendance.pro.tenant.TenantDtos.TenantCreateResponse;
import com.attendance.pro.tenant.TenantDtos.TenantResponse;
import com.attendance.pro.user.MemberService;

/**
 * 테넌트 CRUD/정지 + 최초 TENANT_ADMIN 발급 서비스(SYSTEM_ADMIN 전용 API가 사용).
 * 최초 관리자 등록은 {@link MemberService}에 위임해 UserMapper 접근을 user 패키지에 유지한다.
 */
@Service
public class TenantService {

    private final TenantMapper tenantMapper;
    private final MemberService memberService;

    public TenantService(TenantMapper tenantMapper, MemberService memberService) {
        this.tenantMapper = tenantMapper;
        this.memberService = memberService;
    }

    /**
     * 테넌트 생성 + 최초 TENANT_ADMIN 발급.
     * 초기 비밀번호는 이 응답이 유일한 노출(로그 금지, 재조회 불가).
     */
    @Transactional
    public TenantCreateResponse create(TenantCreateRequest request) {
        if (tenantMapper.existsByCode(request.tenantCode())) {
            throw ApiException.conflict("TENANT_CODE_DUPLICATED", "tenant.code.duplicated");
        }
        TenantCreate create = new TenantCreate(request.tenantCode(), request.name());
        tenantMapper.insert(create);
        MemberService.InitialAdmin admin =
                memberService.registerInitialAdmin(create.getTenantId(), request.adminEmail(), request.adminName());
        return new TenantCreateResponse(create.getTenantId(), request.tenantCode(), request.name(),
                TenantStatus.ACTIVE, admin.userId(), request.adminEmail(), admin.initialPassword());
    }

    public List<TenantResponse> list() {
        return tenantMapper.findAllWithMemberCount();
    }

    public TenantResponse get(long tenantId) {
        TenantResponse tenant = tenantMapper.findByIdWithMemberCount(tenantId);
        if (tenant == null) {
            throw ApiException.notFound("TENANT_NOT_FOUND", "tenant.not-found");
        }
        return tenant;
    }

    /**
     * 정지/재개. 자기 소속 테넌트 정지는 400(운영자 셀프 락아웃 방지).
     */
    @Transactional
    public TenantResponse updateStatus(SessionUser actor, long tenantId, TenantStatus status) {
        if (tenantMapper.findById(tenantId) == null) {
            throw ApiException.notFound("TENANT_NOT_FOUND", "tenant.not-found");
        }
        if (status == TenantStatus.SUSPENDED && actor.tenantId() == tenantId) {
            throw ApiException.badRequest("TENANT_SELF_SUSPEND", "tenant.suspend.self");
        }
        tenantMapper.updateStatus(tenantId, status);
        return get(tenantId);
    }

}
