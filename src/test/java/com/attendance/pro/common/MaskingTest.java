package com.attendance.pro.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.attendance.pro.tenant.TenantDtos.BillingMethod;
import com.attendance.pro.tenant.TenantDtos.TenantBillingRequest;
import com.attendance.pro.tenant.TenantDtos.TenantBillingResponse;
import com.attendance.pro.tenant.TenantDtos.TenantProfileRequest;

/**
 * 마스킹 유틸 + 민감 DTO toString/타입 수준 비노출 테스트.
 * 케이스 ID: test-plan §3-2 MSK-01~06.
 */
class MaskingTest {

    @Test
    @DisplayName("MSK-01: 사업자번호는 앞 3자리만 노출 — 123-**-*****")
    void bizRegNo() {
        assertThat(Masking.bizRegNo("1234567890")).isEqualTo("123-**-*****");
        assertThat(Masking.bizRegNo("123-45-67890")).isEqualTo("123-**-*****");
    }

    @Test
    @DisplayName("MSK-02: 카드는 last4만 입력받아 **** **** **** 1234로 조립")
    void card() {
        assertThat(Masking.card("1234")).isEqualTo("**** **** **** 1234");
    }

    @Test
    @DisplayName("MSK-03: 전화는 가운데 블록 마스킹(국번 길이 가변 안전)")
    void phone() {
        assertThat(Masking.phone("01012345678")).isEqualTo("010-****-5678");
        assertThat(Masking.phone("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(Masking.phone("02-123-4567")).isEqualTo("02-****-4567");
    }

    @Test
    @DisplayName("이메일은 로컬파트 앞 2자만 노출")
    void email() {
        assertThat(Masking.email("contact@acme.co.kr")).isEqualTo("co*****@acme.co.kr");
        assertThat(Masking.email("ab@acme.co.kr")).isEqualTo("**@acme.co.kr");
    }

    @Test
    @DisplayName("MSK-04: null/빈 문자열/비정상 길이는 예외 없이 전체 마스킹 또는 null — 부분 노출 없음")
    void abnormalInputs() {
        assertThat(Masking.bizRegNo(null)).isNull();
        assertThat(Masking.phone(null)).isNull();
        assertThat(Masking.card(null)).isNull();
        assertThat(Masking.email(null)).isNull();
        assertThat(Masking.bizRegNo("")).isEqualTo("***");
        assertThat(Masking.bizRegNo("12")).isEqualTo("***");
        assertThat(Masking.phone("")).isEqualTo("***");
        assertThat(Masking.phone("123")).isEqualTo("***");
        assertThat(Masking.card("")).isEqualTo("***");
        assertThat(Masking.card("12345")).isEqualTo("***");
        assertThat(Masking.email("not-an-email")).isEqualTo("***");
    }

    @Test
    @DisplayName("MSK-05: 민감 요청 DTO의 toString에 평문이 포함되지 않는다")
    void sensitiveRequestToString() {
        TenantProfileRequest profile = new TenantProfileRequest(
                "123-45-67890", "김대표", "04524", "서울시 중구", "10층", "박담당", "contact@acme.co.kr", "010-1234-5678");
        assertThat(profile.toString())
                .doesNotContain("123-45-67890")
                .doesNotContain("010-1234-5678")
                .contains("businessRegNo=***")
                .contains("contactPhone=***");

        TenantBillingRequest billing = new TenantBillingRequest(
                BillingMethod.CARD, "bill@acme.co.kr", "SECRET-BILLING-KEY-7f3a",
                "1234", "VISA", "BASIC", null, null, null, "메모");
        assertThat(billing.toString())
                .doesNotContain("SECRET-BILLING-KEY-7f3a")
                .contains("pgCustomerKey=***");
    }

    @Test
    @DisplayName("MSK-06: 결제 응답 DTO에 빌링키 필드가 타입 수준으로 존재하지 않는다")
    void billingResponseHasNoBillingKeyField() {
        String[] names = Arrays.stream(TenantBillingResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .map(n -> n.toLowerCase(Locale.ROOT))
                .toArray(String[]::new);
        //존재 여부 불리언(hasBillingKey)만 허용 — 키 원문/암호문을 담는 필드는 존재하지 않는다
        assertThat(names).noneMatch(n -> n.contains("pgcustomerkey")
                || (n.contains("billingkey") && !n.equals("hasbillingkey")));
        assertThat(names).contains("hasbillingkey");
    }

}
