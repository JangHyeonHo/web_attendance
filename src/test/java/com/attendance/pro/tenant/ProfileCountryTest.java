package com.attendance.pro.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 소재국별 사업자 식별번호 검증·마스킹 규칙(KR 사업자등록번호 / JP 法人番号).
 */
class ProfileCountryTest {

    @Test
    @DisplayName("KR: ###-##-##### 형식만 유효")
    void krFormat() {
        assertThat(ProfileCountry.KR.isValidBizRegNo("123-45-67890")).isTrue();
        assertThat(ProfileCountry.KR.isValidBizRegNo("1234567890")).isFalse();   //하이픈 없음
        assertThat(ProfileCountry.KR.isValidBizRegNo("12-345-67890")).isFalse(); //블록 구성 상이
        assertThat(ProfileCountry.KR.isValidBizRegNo("1234567890123")).isFalse();//JP 형식
        assertThat(ProfileCountry.KR.isValidBizRegNo(null)).isFalse();
    }

    @Test
    @DisplayName("JP: 13자리 숫자만 유효")
    void jpFormat() {
        assertThat(ProfileCountry.JP.isValidBizRegNo("1234567890123")).isTrue();
        assertThat(ProfileCountry.JP.isValidBizRegNo("123456789012")).isFalse();  //12자리
        assertThat(ProfileCountry.JP.isValidBizRegNo("12345678901234")).isFalse();//14자리
        assertThat(ProfileCountry.JP.isValidBizRegNo("123-45-67890")).isFalse();  //KR 형식
        assertThat(ProfileCountry.JP.isValidBizRegNo(null)).isFalse();
    }

    @Test
    @DisplayName("마스킹: KR 앞 3자리만 / JP 말미 4자리만 노출")
    void masking() {
        assertThat(ProfileCountry.KR.maskBizRegNo("123-45-67890")).isEqualTo("123-**-*****");
        assertThat(ProfileCountry.JP.maskBizRegNo("1234567890123")).isEqualTo("*********0123");
        //형식 불량은 부분 노출 없이 전체 마스킹
        assertThat(ProfileCountry.JP.maskBizRegNo("123")).isEqualTo("***");
    }

    @Test
    @DisplayName("of(): 대소문자 무관 해석, 미지원 국가는 null")
    void ofResolvesCode() {
        assertThat(ProfileCountry.of("KR")).isEqualTo(ProfileCountry.KR);
        assertThat(ProfileCountry.of("jp")).isEqualTo(ProfileCountry.JP);
        assertThat(ProfileCountry.of(" kr ")).isEqualTo(ProfileCountry.KR);
        assertThat(ProfileCountry.of("US")).isNull();
        assertThat(ProfileCountry.of(null)).isNull();
    }

    @Test
    @DisplayName("국가별 형식 오류 메시지 키가 구분된다")
    void formatMessageKeys() {
        assertThat(ProfileCountry.KR.formatMessageKey()).isEqualTo("validation.biz-reg-no.format-kr");
        assertThat(ProfileCountry.JP.formatMessageKey()).isEqualTo("validation.biz-reg-no.format-jp");
    }

}
