package com.attendance.pro.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.common.FieldCipher;
import com.attendance.pro.tenant.TenantDtos.TenantProfileRequest;
import com.attendance.pro.tenant.TenantDtos.TenantProfileResponse;

/**
 * 기업 정보 서비스 테스트 — HOL-08: 검증·마스킹의 국가가 tenant.country에서 유래(요청에 country 없음).
 */
@ExtendWith(MockitoExtension.class)
class TenantProfileServiceTest {

    private static final long TENANT_ID = 10L;
    /** application.properties의 개발 기본 키와 동일한 32바이트 base64 */
    private static final String TEST_KEY = "d2ViLWF0dGVuZGFuY2UtZGV2LWNyeXB0by1rZXktMzI=";

    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private TenantProfileMapper tenantProfileMapper;
    @Mock
    private TenantBillingMapper tenantBillingMapper;

    private final FieldCipher fieldCipher = new FieldCipher(TEST_KEY);

    private TenantProfileService service() {
        return new TenantProfileService(tenantMapper, tenantProfileMapper, tenantBillingMapper, fieldCipher);
    }

    private static Tenant tenant(String country) {
        return new Tenant(TENANT_ID, "ACME", "에이크미(주)", country, TenantStatus.ACTIVE, LocalDateTime.now());
    }

    private static TenantProfileRequest request(String bizRegNo) {
        return new TenantProfileRequest(bizRegNo, "홍대표", "서울시", "김담당", "c@acme.co.kr", "010-1234-5678");
    }

    private void stubProfileReload(String country, String bizRegNoPlain) {
        when(tenantProfileMapper.findById(TENANT_ID)).thenAnswer(inv -> new TenantProfile(
                TENANT_ID, country, fieldCipher.encrypt(bizRegNoPlain),
                "홍대표", "서울시", "김담당", "c@acme.co.kr",
                fieldCipher.encrypt("010-1234-5678"),
                LocalDateTime.now(), LocalDateTime.now()));
    }

    @Test
    @DisplayName("HOL-08a: KR 테넌트에 JP 형식(13자리)은 400 BIZ_REG_NO_FORMAT — 국가는 tenant.country 유래")
    void krTenantRejectsJpFormat() {
        when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));

        assertThatThrownBy(() -> service().upsertProfile(TENANT_ID, request("1234567890123")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(400);
                    assertThat(apiException.getCode()).isEqualTo("BIZ_REG_NO_FORMAT");
                    assertThat(apiException.getMessageKey()).isEqualTo("validation.biz-reg-no.format-kr");
                });
        verify(tenantProfileMapper, never()).upsert(anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("HOL-08b: JP 테넌트는 法人番号 13자리를 통과, KR 형식은 400(형식 키도 JP)")
    void jpTenantValidatesJpFormat() {
        when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("JP"));
        stubProfileReload("JP", "1234567890123");

        TenantProfileResponse response = service().upsertProfile(TENANT_ID, request("1234567890123"));
        assertThat(response.businessRegNoMasked()).isEqualTo("*********0123");

        assertThatThrownBy(() -> service().upsertProfile(TENANT_ID, request("123-45-67890")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getMessageKey())
                        .isEqualTo("validation.biz-reg-no.format-jp"));
    }

    @Test
    @DisplayName("KR 테넌트 정상 등록: 암호화 저장 + 응답은 마스킹값(앞 3자리만)")
    void krTenantUpsertMasksResponse() {
        when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
        stubProfileReload("KR", "123-45-67890");

        TenantProfileResponse response = service().upsertProfile(TENANT_ID, request("123-45-67890"));

        assertThat(response.country()).isEqualTo("KR");
        assertThat(response.businessRegNoMasked()).isEqualTo("123-**-*****");
        assertThat(response.contactPhoneMasked()).isEqualTo("010-****-5678");
    }

    @Test
    @DisplayName("미존재 테넌트는 404 TENANT_NOT_FOUND")
    void tenantNotFound() {
        when(tenantMapper.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service().upsertProfile(99L, request("123-45-67890")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("TENANT_NOT_FOUND"));
    }

}
