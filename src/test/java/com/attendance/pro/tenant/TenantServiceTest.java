package com.attendance.pro.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.tenant.TenantDtos.TenantCreateRequest;
import com.attendance.pro.tenant.TenantDtos.TenantCreateResponse;
import com.attendance.pro.tenant.TenantDtos.TenantResponse;
import com.attendance.pro.user.MemberService;
import com.attendance.pro.user.Role;

/**
 * 테넌트 CRUD/정지 + 최초 관리자 발급 테스트(코드 중복 409, 자기 테넌트 정지 400 포함).
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    private static final SessionUser SYSTEM_ADMIN =
            new SessionUser(1L, 1L, "DEFAULT", "기본 테넌트", "admin@attendance.local", "관리자", Role.SYSTEM_ADMIN);

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private MemberService memberService;

    private TenantService service() {
        return new TenantService(tenantMapper, memberService);
    }

    @Test
    @DisplayName("테넌트 생성: tenant INSERT + 최초 TENANT_ADMIN 발급, 초기 비밀번호는 응답에 1회 반환")
    void createIssuesInitialAdmin() {
        when(tenantMapper.existsByCode("ACME")).thenReturn(false);
        when(tenantMapper.insert(any(TenantCreate.class))).thenAnswer(inv -> {
            inv.getArgument(0, TenantCreate.class).setTenantId(10L);
            return 1;
        });
        when(memberService.registerInitialAdmin(10L, "admin@acme.co.kr", "김관리"))
                .thenReturn(new MemberService.InitialAdmin(100L, "Initial1!pwd"));

        TenantCreateResponse response = service().create(
                new TenantCreateRequest("ACME", "에이크미(주)", "admin@acme.co.kr", "김관리"));

        assertThat(response.tenantId()).isEqualTo(10L);
        assertThat(response.tenantCode()).isEqualTo("ACME");
        assertThat(response.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(response.adminUserId()).isEqualTo(100L);
        assertThat(response.adminEmail()).isEqualTo("admin@acme.co.kr");
        assertThat(response.initialPassword()).isEqualTo("Initial1!pwd");
        verify(memberService).registerInitialAdmin(10L, "admin@acme.co.kr", "김관리");
    }

    @Test
    @DisplayName("테넌트 코드 중복이면 409 TENANT_CODE_DUPLICATED — 관리자 발급 없음")
    void duplicateCodeRejected() {
        when(tenantMapper.existsByCode("ACME")).thenReturn(true);

        assertThatThrownBy(() -> service().create(
                new TenantCreateRequest("ACME", "에이크미(주)", "admin@acme.co.kr", "김관리")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(409);
                    assertThat(apiException.getCode()).isEqualTo("TENANT_CODE_DUPLICATED");
                    assertThat(apiException.getMessageKey()).isEqualTo("tenant.code.duplicated");
                });
        verify(memberService, never()).registerInitialAdmin(org.mockito.ArgumentMatchers.anyLong(), any(), any());
    }

    @Test
    @DisplayName("미존재 테넌트 조회는 404 TENANT_NOT_FOUND")
    void getNotFound() {
        when(tenantMapper.findByIdWithMemberCount(99L)).thenReturn(null);
        assertThatThrownBy(() -> service().get(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("TENANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("자기 소속 테넌트 정지 시도는 400 TENANT_SELF_SUSPEND")
    void selfSuspendRejected() {
        when(tenantMapper.findById(1L))
                .thenReturn(new Tenant(1L, "DEFAULT", "기본 테넌트", TenantStatus.ACTIVE, LocalDateTime.now()));

        assertThatThrownBy(() -> service().updateStatus(SYSTEM_ADMIN, 1L, TenantStatus.SUSPENDED))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(400);
                    assertThat(apiException.getCode()).isEqualTo("TENANT_SELF_SUSPEND");
                });
        verify(tenantMapper, never()).updateStatus(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("타 테넌트 정지/재개는 정상 처리된다")
    void suspendOtherTenant() {
        when(tenantMapper.findById(10L))
                .thenReturn(new Tenant(10L, "ACME", "에이크미(주)", TenantStatus.ACTIVE, LocalDateTime.now()));
        when(tenantMapper.findByIdWithMemberCount(10L))
                .thenReturn(new TenantResponse(10L, "ACME", "에이크미(주)", TenantStatus.SUSPENDED, 3,
                        LocalDateTime.now()));

        TenantResponse response = service().updateStatus(SYSTEM_ADMIN, 10L, TenantStatus.SUSPENDED);

        assertThat(response.status()).isEqualTo(TenantStatus.SUSPENDED);
        verify(tenantMapper).updateStatus(10L, TenantStatus.SUSPENDED);
    }

    @Test
    @DisplayName("미존재 테넌트 정지는 404")
    void suspendNotFound() {
        when(tenantMapper.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service().updateStatus(SYSTEM_ADMIN, 99L, TenantStatus.SUSPENDED))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("TENANT_NOT_FOUND"));
    }

}
