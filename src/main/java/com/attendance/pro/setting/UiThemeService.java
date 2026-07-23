package com.attendance.pro.setting;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.attendance.pro.common.ApiException;

/**
 * 시스템 전역 UI 테마 설정 서비스.
 * 저장값(AUTO 포함)은 SYSTEM_ADMIN 설정 화면(A005)이, 확정값은 화면 전개(navigation)가 쓴다.
 */
@Service
public class UiThemeService {

    static final String KEY = "UI_THEME";

    private final AppSettingMapper appSettingMapper;
    private final Clock clock;

    //생성자가 2개(테스트용 Clock 주입)이므로 스프링이 쓸 쪽을 명시한다(HolidayService와 동일 방식)
    @Autowired
    public UiThemeService(AppSettingMapper appSettingMapper) {
        this(appSettingMapper, Clock.systemDefaultZone());
    }

    UiThemeService(AppSettingMapper appSettingMapper, Clock clock) {
        this.appSettingMapper = appSettingMapper;
        this.clock = clock;
    }

    /** 저장된 설정값 — 행 누락/미지 문자열은 AUTO로 폴백(테마는 부가 기능, 기동을 막지 않는다) */
    public UiTheme setting() {
        UiTheme stored = UiTheme.of(appSettingMapper.findValue(KEY));
        return stored == null ? UiTheme.AUTO : stored;
    }

    /** 화면에 적용할 확정 테마(AUTO는 서버 날짜의 계절로 해석) */
    public UiTheme resolved() {
        return setting().resolve(LocalDate.now(clock));
    }

    /** 설정 변경 — 미지원 값은 400(THEME_UNSUPPORTED) */
    public UiTheme update(String value) {
        UiTheme theme = UiTheme.of(value);
        if (theme == null) {
            throw ApiException.badRequest("THEME_UNSUPPORTED", "validation.theme.supported");
        }
        appSettingMapper.upsert(KEY, theme.name());
        return theme;
    }

}
