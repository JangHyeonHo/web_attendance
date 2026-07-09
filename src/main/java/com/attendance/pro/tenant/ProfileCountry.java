package com.attendance.pro.tenant;

import java.util.regex.Pattern;

import com.attendance.pro.common.Masking;

/**
 * 테넌트 소재국별 사업자 식별번호 규칙(검증 + 마스킹).
 *
 * 사업자 식별번호 체계는 화면 언어가 아니라 "고객사 소재국"의 속성이다:
 *  - KR: 사업자등록번호 10자리(###-##-#####)
 *  - JP: 法人番号 13자리(하이픈 없음) — 일본은 공개 정보지만 보수적 일관 보호(암호화+마스킹) 유지
 * 신규 국가는 이 enum에 추가한다(라벨 키 BIZ_REG_NO_{국가코드}도 언어 마스터에 시드).
 */
public enum ProfileCountry {

    /** 한국 — 사업자등록번호 */
    KR("^\\d{3}-\\d{2}-\\d{5}$", "validation.biz-reg-no.format-kr") {
        @Override
        public String maskBizRegNo(String plain) {
            return Masking.bizRegNo(plain);
        }
    },

    /** 일본 — 法人番号(법인번호) */
    JP("^\\d{13}$", "validation.biz-reg-no.format-jp") {
        @Override
        public String maskBizRegNo(String plain) {
            return Masking.corpNo13(plain);
        }
    };

    private final Pattern bizRegNoPattern;
    private final String formatMessageKey;

    ProfileCountry(String bizRegNoRegex, String formatMessageKey) {
        this.bizRegNoPattern = Pattern.compile(bizRegNoRegex);
        this.formatMessageKey = formatMessageKey;
    }

    public boolean isValidBizRegNo(String value) {
        return value != null && bizRegNoPattern.matcher(value).matches();
    }

    /** 형식 불일치시 사용할 메시지 키(국가별 형식 안내) */
    public String formatMessageKey() {
        return formatMessageKey;
    }

    /** 국가별 마스킹 — 부분 노출 규칙이 자릿수 구조에 의존하므로 국가가 결정한다 */
    public abstract String maskBizRegNo(String plain);

    /** 지원 국가 코드면 해당 enum, 아니면 null(호출부에서 400) */
    public static ProfileCountry of(String code) {
        if (code == null) {
            return null;
        }
        for (ProfileCountry country : values()) {
            if (country.name().equalsIgnoreCase(code.trim())) {
                return country;
            }
        }
        return null;
    }

}
