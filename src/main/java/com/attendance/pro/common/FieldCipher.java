package com.attendance.pro.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 민감 필드의 애플리케이션 레벨 암호화(AES-256-GCM, JCA Cipher 직접 사용).
 * 암호문은 텍스트 포맷 {@code v1:{base64(iv12)}:{base64(ct||tag)}} — 키 버전 프리픽스로 로테이션 대비.
 * 반환 문자열(ASCII)을 VARCHAR 컬럼에 그대로 저장한다.
 *
 * 키: 환경변수 APP_CRYPTO_KEY = base64 인코딩된 32바이트(openssl rand -base64 32).
 * 잘못된 키(디코딩 실패/길이 불일치)면 빈 생성에서 즉시 예외 → 앱 기동 실패(fail-fast).
 */
@Component
public class FieldCipher {

    private static final Logger log = LoggerFactory.getLogger(FieldCipher.class);

    private static final String VERSION = "v1";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    /** application.properties의 개발 기본 키(공개 값) — 이 키 사용 중임을 기동시 경고하기 위한 대조용 */
    private static final String DEV_DEFAULT_KEY = "d2ViLWF0dGVuZGFuY2UtZGV2LWNyeXB0by1rZXktMzI=";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(@Value("${app.crypto.key}") String base64Key,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_CRYPTO_KEY must be base64 of 32 bytes", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("APP_CRYPTO_KEY must be base64 of 32 bytes");
        }
        if (DEV_DEFAULT_KEY.equals(base64Key.trim())) {
            //저장소에 커밋된 공개 키 — dev/test/local 프로파일에서만 허용하고, 그 외(운영·프로파일 미지정)에서는
            //조용히 공개 키로 암호화하는 사고를 막기 위해 기동 자체를 실패시킨다(warn→fail-fast).
            if (!isDevLikeProfile(activeProfiles)) {
                throw new IllegalStateException("커밋된 개발용 기본 암호화 키는 dev/test/local 프로파일에서만 허용됩니다. "
                        + "운영은 SPRING_PROFILES_ACTIVE=prod + APP_CRYPTO_KEY 주입으로 기동하세요.");
            }
            log.warn("개발 기본 암호화 키를 사용 중입니다(dev/test). 이 키로 암호화된 데이터는 운영 반입 금지.");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** dev/test/local 프로파일이 하나라도 활성인지 — 개발 기본 키 허용 판단용. */
    private static boolean isDevLikeProfile(String activeProfiles) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        for (String p : activeProfiles.split(",")) {
            String t = p.trim().toLowerCase(java.util.Locale.ROOT);
            if (t.equals("dev") || t.equals("test") || t.equals("local")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 평문 → "v1:{b64(iv)}:{b64(ct||tag)}" 텍스트 암호문. null은 null.
     * IV 12바이트는 매회 SecureRandom 생성(GCM에서 (키,IV) 재사용은 치명적).
     */
    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return VERSION + ":" + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(ct);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("field encryption failed", e);
        }
    }

    /**
     * 복호화. "v1:" 프리픽스로 키 버전 선택(현재 v1 단일, 미지 버전·형식 불량은 예외). null은 null.
     * 태그 불일치(변조/키 불일치)는 데이터 무결성 사고이므로 예외를 그대로 올린다(값은 로그 금지).
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        String[] parts = stored.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalStateException("invalid ciphertext format (missing version prefix)");
        }
        if (!VERSION.equals(parts[0])) {
            throw new IllegalStateException("unknown cipher key version: " + parts[0]);
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] ct = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("field decryption failed", e);
        }
    }

}
