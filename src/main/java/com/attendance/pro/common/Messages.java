package com.attendance.pro.common;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * 요청 로케일(세션 언어 → Accept-Language → 한국어) 기반 메시지 해석 헬퍼.
 * 로케일은 {@code LocaleResolver}(LocaleConfig)가 요청마다 LocaleContextHolder에 설정한다.
 */
@Component
public class Messages {

    private final MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 현재 요청 로케일로 메시지를 해석한다.
     *
     * @param code 메시지 키
     * @param args MessageFormat 인자({0}, {1}...)
     */
    public String get(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

}
