package com.attendance.pro.tenant;

import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청 Host의 서브도메인 → 테넌트 해석 (서브도메인/코드 병행 방식의 호스트 절반).
 *
 * app.tenant.base-domain(예: webatt.example)이 설정되어 있고 요청 호스트가
 * {label}.{base-domain} 형태이면 label을 테넌트 코드로 해석한다.
 * base-domain 미설정(기본값)이면 항상 NONE — 기존 코드 입력 방식만 동작한다.
 *
 * 해석 결과 3종:
 *  - NONE:    루트 도메인/예약 서브도메인/다단 라벨/base-domain 미설정 → 코드 입력 방식으로
 *  - FOUND:   테넌트 확정 — 이 요청의 테넌트는 호스트가 정한다(바디 코드와 불일치시 400은 호출부 책임)
 *  - UNKNOWN: 서브도메인 형태이지만 미등록 코드 — 로그인은 통일 401(존재 비노출), 세션은 불인정
 *
 * 결과는 요청 속성에 캐시해 요청당 DB 조회를 1회로 제한한다.
 */
@Component
public class TenantHostResolver {

    /** 테넌트 코드로 쓸 수 없는 예약 서브도메인(운영/인프라 용도 선점) */
    public static final Set<String> RESERVED_LABELS = Set.of("WWW", "ADMIN", "API", "APP", "MAIL");

    private static final String REQUEST_ATTR = TenantHostResolver.class.getName() + ".RESULT";

    private final TenantMapper tenantMapper;
    private final String baseDomain;

    public TenantHostResolver(TenantMapper tenantMapper,
            @Value("${app.tenant.base-domain:}") String baseDomain) {
        this.tenantMapper = tenantMapper;
        this.baseDomain = baseDomain == null ? "" : baseDomain.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 요청 호스트를 해석한다(요청당 1회, 이후는 요청 속성 캐시).
     */
    public HostTenant resolve(HttpServletRequest request) {
        HostTenant cached = (HostTenant) request.getAttribute(REQUEST_ATTR);
        if (cached != null) {
            return cached;
        }
        HostTenant result = resolveHost(request.getServerName());
        request.setAttribute(REQUEST_ATTR, result);
        return result;
    }

    /** 호스트명만으로 해석(단위 테스트 진입점 — getServerName()은 포트가 이미 제거된 값). */
    HostTenant resolveHost(String serverName) {
        if (baseDomain.isEmpty() || serverName == null) {
            return HostTenant.none();
        }
        String host = serverName.toLowerCase(Locale.ROOT);
        if (!host.endsWith("." + baseDomain)) {
            return HostTenant.none();
        }
        String label = host.substring(0, host.length() - baseDomain.length() - 1);
        //다단 라벨(a.b.base)은 테넌트 주소가 아니다
        if (label.isEmpty() || label.contains(".")) {
            return HostTenant.none();
        }
        String code = label.toUpperCase(Locale.ROOT);
        if (RESERVED_LABELS.contains(code)) {
            return HostTenant.none();
        }
        Tenant tenant = tenantMapper.findByCode(code);
        return tenant == null ? HostTenant.unknown(code) : HostTenant.found(tenant);
    }

    /**
     * 호스트 해석 결과. FOUND일 때만 tenant가 채워진다.
     */
    public record HostTenant(Kind kind, Tenant tenant, String code) {

        public enum Kind {
            NONE, FOUND, UNKNOWN
        }

        static HostTenant none() {
            return new HostTenant(Kind.NONE, null, null);
        }

        static HostTenant found(Tenant tenant) {
            return new HostTenant(Kind.FOUND, tenant, tenant.tenantCode());
        }

        static HostTenant unknown(String code) {
            return new HostTenant(Kind.UNKNOWN, null, code);
        }

        /** 호스트가 테넌트를 주장하는가(FOUND/UNKNOWN) — 이때 바디 코드보다 호스트가 우선한다 */
        public boolean claimsTenant() {
            return kind != Kind.NONE;
        }
    }

}
