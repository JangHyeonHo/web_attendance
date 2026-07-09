package com.attendance.pro.common;

/**
 * 민감 값 마스킹 정적 유틸.
 * 마스킹은 응답 DTO 조립 시점에만 적용한다(저장값은 항상 암호문 원본).
 * 비정상 입력(형식 불량)은 예외 없이 전체 마스킹("***") — 부분 노출이 절대 없다. null은 null.
 */
public final class Masking {

    private static final String FULL_MASK = "***";

    private Masking() {
    }

    /**
     * 사업자등록번호: {@code 123-45-67890} → {@code 123-**-*****} (앞 3자리만 노출).
     * 하이픈 없는 입력도 정규화 후 적용한다.
     */
    public static String bizRegNo(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() != 10) {
            return FULL_MASK;
        }
        return digits.substring(0, 3) + "-**-*****";
    }

    /**
     * 일본 법인번호(法人番号) 13자리: {@code 1234567890123} → {@code *********0123} (말미 4자리만 노출).
     */
    public static String corpNo13(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() != 13) {
            return FULL_MASK;
        }
        return "*".repeat(9) + digits.substring(9);
    }

    /**
     * 전화번호: {@code 010-1234-5678} → {@code 010-****-5678} (가운데 블록 마스킹, 국번 길이 가변 안전).
     */
    public static String phone(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 9 || digits.length() > 15) {
            return FULL_MASK;
        }
        String prefix = digits.startsWith("02") ? "02" : digits.substring(0, 3);
        String last4 = digits.substring(digits.length() - 4);
        return prefix + "-****-" + last4;
    }

    /**
     * 카드: 원본(PAN)은 애초에 저장하지 않으므로 입력은 last4뿐 —
     * {@code 1234} → {@code **** **** **** 1234}.
     */
    public static String card(String last4) {
        if (last4 == null) {
            return null;
        }
        if (!last4.matches("\\d{4}")) {
            return FULL_MASK;
        }
        return "**** **** **** " + last4;
    }

    /**
     * 이메일: {@code contact@acme.co.kr} → {@code co*****@acme.co.kr}
     * (로컬파트 앞 2자 노출 + 나머지 *, 도메인 노출. 로컬파트 2자 이하면 전부 *).
     */
    public static String email(String value) {
        if (value == null) {
            return null;
        }
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            return FULL_MASK;
        }
        String local = value.substring(0, at);
        String domain = value.substring(at + 1);
        if (local.length() <= 2) {
            return "*".repeat(local.length()) + "@" + domain;
        }
        return local.substring(0, 2) + "*".repeat(local.length() - 2) + "@" + domain;
    }

}
