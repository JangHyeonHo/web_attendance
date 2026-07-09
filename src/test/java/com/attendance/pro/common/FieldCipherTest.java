package com.attendance.pro.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * AES-256-GCM 필드 암호화 유틸 테스트.
 * 케이스 ID: test-plan §3-1 CRY-01/02/04/05/07 + 키 검증.
 */
class FieldCipherTest {

    /** 테스트용 base64(32바이트) 키 */
    private static final String KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes());

    private final FieldCipher cipher = new FieldCipher(KEY);

    @ParameterizedTest
    @ValueSource(strings = {
            "123-45-67890",                      //사업자번호
            "010-1234-5678",                     //전화
            "billing-key-0123456789abcdef",      //빌링키
            "한글 값 그리고 공백  포함",            //한글/공백
            "x"
    })
    @DisplayName("CRY-01: 라운드트립 — decrypt(encrypt(x)) == x")
    void roundTrip(String plain) {
        assertThat(cipher.decrypt(cipher.encrypt(plain))).isEqualTo(plain);
    }

    @Test
    @DisplayName("CRY-01: 최대 길이(255자) 값도 라운드트립된다")
    void roundTripMaxLength() {
        String plain = "K".repeat(255);
        assertThat(cipher.decrypt(cipher.encrypt(plain))).isEqualTo(plain);
    }

    @Test
    @DisplayName("null은 null 그대로 통과한다")
    void nullPassThrough() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("CRY-02: 암호문이 v1:{base64(iv12)}:{base64(ct||tag)} 텍스트 형식이다")
    void versionPrefixFormat() {
        String stored = cipher.encrypt("123-45-67890");
        String[] parts = stored.split(":", 3);
        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isEqualTo("v1");
        assertThat(Base64.getDecoder().decode(parts[1])).hasSize(12);                //IV 12바이트
        //ct||tag: 평문 12바이트 + GCM 태그 16바이트
        assertThat(Base64.getDecoder().decode(parts[2])).hasSize("123-45-67890".getBytes().length + 16);
    }

    @Test
    @DisplayName("CRY-02: v1: 프리픽스 없는 입력은 명시적 예외(레거시 평문 오인 방지)")
    void missingPrefixRejected() {
        assertThatThrownBy(() -> cipher.decrypt("plain-text-without-prefix"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CRY-07: 미지 키 버전(v9:)은 명시적 예외")
    void unknownVersionRejected() {
        String stored = cipher.encrypt("value");
        String tampered = "v9" + stored.substring(2);
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("v9");
    }

    @Test
    @DisplayName("CRY-04: 암호문 변조(GCM 태그 불일치)는 복호화 실패 — silent corruption 없음")
    void tamperedCiphertextRejected() {
        String stored = cipher.encrypt("123-45-67890");
        String[] parts = stored.split(":", 3);
        byte[] ct = Base64.getDecoder().decode(parts[2]);
        ct[ct.length / 2] ^= 0x01;  //중간 바이트 변조
        String tampered = parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(ct);
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CRY-05: 같은 평문 2회 암호화는 서로 다른 암호문(IV 매회 랜덤)")
    void randomIvPerEncryption() {
        String first = cipher.encrypt("123-45-67890");
        String second = cipher.encrypt("123-45-67890");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("잘못된 키(base64 32바이트 아님)는 생성 즉시 예외 — 기동 실패(fail-fast)")
    void invalidKeyFailsFast() {
        //길이 불일치
        String shortKey = Base64.getEncoder().encodeToString("short-key".getBytes());
        assertThatThrownBy(() -> new FieldCipher(shortKey)).isInstanceOf(IllegalStateException.class);
        //base64 디코딩 불가
        assertThatThrownBy(() -> new FieldCipher("!!not-base64!!")).isInstanceOf(IllegalStateException.class);
    }

}
