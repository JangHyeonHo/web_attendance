package com.attendance.pro.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.common.ApiException;

/**
 * UI 테마 설정 서비스 테스트 — 폴백·AUTO 해석·미지원 값 거부.
 */
@ExtendWith(MockitoExtension.class)
class UiThemeServiceTest {

    @Mock
    private AppSettingMapper appSettingMapper;

    /** 2026-07-10(여름) 고정 시계 */
    private static final Clock SUMMER_CLOCK =
            Clock.fixed(Instant.parse("2026-07-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private UiThemeService service() {
        return new UiThemeService(appSettingMapper, SUMMER_CLOCK);
    }

    @Test
    @DisplayName("setting: 행 누락/미지 문자열은 AUTO 폴백(테마는 부가 기능 — 실패해도 화면 전개는 계속)")
    void settingFallsBackToAuto() {
        when(appSettingMapper.findValue(UiThemeService.KEY)).thenReturn(null);
        assertThat(service().setting()).isEqualTo(UiTheme.AUTO);

        when(appSettingMapper.findValue(UiThemeService.KEY)).thenReturn("NEON");
        assertThat(service().setting()).isEqualTo(UiTheme.AUTO);
    }

    @Test
    @DisplayName("resolved: AUTO는 서버 날짜의 계절로, 고정값은 그대로")
    void resolved() {
        when(appSettingMapper.findValue(UiThemeService.KEY)).thenReturn("AUTO");
        assertThat(service().resolved()).isEqualTo(UiTheme.SUMMER);

        when(appSettingMapper.findValue(UiThemeService.KEY)).thenReturn("WINTER");
        assertThat(service().resolved()).isEqualTo(UiTheme.WINTER);
    }

    @Test
    @DisplayName("update: 정상값은 upsert, 미지원 값은 400(THEME_UNSUPPORTED) + DB 무변경")
    void update() {
        assertThat(service().update("SPRING")).isEqualTo(UiTheme.SPRING);
        verify(appSettingMapper).upsert(UiThemeService.KEY, "SPRING");

        assertThatThrownBy(() -> service().update("spring"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "THEME_UNSUPPORTED");
        assertThatThrownBy(() -> service().update(null)).isInstanceOf(ApiException.class);
        //미지원 값 거부 경로에서 DB 쓰기 없음(정상 경로의 upsert 1회가 유일한 호출)
        verifyNoMoreInteractions(appSettingMapper);
    }

}
