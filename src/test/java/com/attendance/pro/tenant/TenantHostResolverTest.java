package com.attendance.pro.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import com.attendance.pro.tenant.TenantHostResolver.HostTenant;
import com.attendance.pro.tenant.TenantHostResolver.HostTenant.Kind;

/**
 * 서브도메인 → 테넌트 해석 규칙(병행 방식의 호스트 절반).
 */
@ExtendWith(MockitoExtension.class)
class TenantHostResolverTest {

    private static final Tenant ACME =
            new Tenant(10L, "ACME", "에이크미(주)", "KR", TenantStatus.ACTIVE, LocalDateTime.now());

    @Mock
    private TenantMapper tenantMapper;

    private TenantHostResolver resolver(String baseDomain) {
        return new TenantHostResolver(tenantMapper, baseDomain);
    }

    @Test
    @DisplayName("base-domain 미설정(기본값)이면 항상 NONE — 기존 코드 방식만 동작")
    void disabledWithoutBaseDomain() {
        assertThat(resolver("").resolveHost("acme.webatt.example").kind()).isEqualTo(Kind.NONE);
        verifyNoInteractions(tenantMapper);
    }

    @Test
    @DisplayName("테넌트 서브도메인은 대문자 정규화 후 코드로 해석(FOUND)")
    void resolvesSubdomainToTenant() {
        when(tenantMapper.findByCode("ACME")).thenReturn(ACME);

        HostTenant result = resolver("webatt.example").resolveHost("acme.webatt.example");

        assertThat(result.kind()).isEqualTo(Kind.FOUND);
        assertThat(result.tenant()).isEqualTo(ACME);
        assertThat(result.code()).isEqualTo("ACME");
    }

    @Test
    @DisplayName("미등록 서브도메인은 UNKNOWN — 폴백하지 않고 호스트가 테넌트를 주장한 것으로 본다")
    void unknownSubdomain() {
        when(tenantMapper.findByCode("NOPE")).thenReturn(null);

        HostTenant result = resolver("webatt.example").resolveHost("nope.webatt.example");

        assertThat(result.kind()).isEqualTo(Kind.UNKNOWN);
        assertThat(result.claimsTenant()).isTrue();
        assertThat(result.code()).isEqualTo("NOPE");
    }

    @Test
    @DisplayName("루트 도메인/무관 호스트/다단 라벨/IP·localhost는 NONE")
    void noneForNonTenantHosts() {
        TenantHostResolver resolver = resolver("webatt.example");
        assertThat(resolver.resolveHost("webatt.example").kind()).isEqualTo(Kind.NONE);      //루트
        assertThat(resolver.resolveHost("other.example").kind()).isEqualTo(Kind.NONE);       //무관 도메인
        assertThat(resolver.resolveHost("a.b.webatt.example").kind()).isEqualTo(Kind.NONE);  //다단 라벨
        assertThat(resolver.resolveHost("localhost").kind()).isEqualTo(Kind.NONE);
        assertThat(resolver.resolveHost("127.0.0.1").kind()).isEqualTo(Kind.NONE);
        assertThat(resolver.resolveHost(null).kind()).isEqualTo(Kind.NONE);
        verifyNoInteractions(tenantMapper);
    }

    @Test
    @DisplayName("예약 서브도메인(www/admin/api/app/mail)은 테넌트로 해석하지 않는다")
    void reservedLabelsAreNone() {
        TenantHostResolver resolver = resolver("webatt.example");
        for (String reserved : TenantHostResolver.RESERVED_LABELS) {
            String host = reserved.toLowerCase() + ".webatt.example";
            assertThat(resolver.resolveHost(host).kind()).as(host).isEqualTo(Kind.NONE);
        }
        verifyNoInteractions(tenantMapper);
    }

    @Test
    @DisplayName("요청 단위 캐시: 같은 요청에서 두 번 해석해도 DB 조회는 1회")
    void cachesPerRequest() {
        when(tenantMapper.findByCode("ACME")).thenReturn(ACME);
        TenantHostResolver resolver = resolver("webatt.example");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("acme.webatt.example");

        HostTenant first = resolver.resolve(request);
        HostTenant second = resolver.resolve(request);

        assertThat(first).isSameAs(second);
        verify(tenantMapper).findByCode("ACME"); //1회만
    }

}
