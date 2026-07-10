-- =========================================================
-- V11: Phase 4 — 시스템 전역 UI 테마 설정 + 관련 화면 텍스트 시드
--  - app_setting: 시스템 전역 키-값 설정(테넌트 소속 없음 — SYSTEM_ADMIN만 변경)
--  - UI_THEME 기본값 AUTO: 서버 날짜의 계절로 자동 해석(3-5월 봄 / 6-8월 여름 / 9-11월 가을 / 12-2월 겨울)
--  - 전 구문 재실행 내성(V4 방식)
-- =========================================================

CREATE TABLE IF NOT EXISTS app_setting (
    setting_key   VARCHAR(50)  NOT NULL COMMENT '설정 키',
    setting_value VARCHAR(100) NOT NULL COMMENT '설정 값',
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
        COMMENT '수정 시각',
    PRIMARY KEY (setting_key)
) COMMENT='시스템 전역 설정(키-값). Phase 4: UI_THEME';

INSERT IGNORE INTO app_setting (setting_key, setting_value) VALUES ('UI_THEME', 'AUTO');

-- ---------------------------------------------------------
-- W004(관리): 테마 설정 섹션
-- ---------------------------------------------------------
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W004','THEME_TITLE','KOR','테마 설정'),
('W004','THEME_TITLE','ENG','Theme'),
('W004','THEME_TITLE','JPN','テーマ設定'),
('W004','THEME_DESC','KOR','모든 화면에 적용되는 계절 테마입니다. 자동은 현재 계절을 따릅니다.'),
('W004','THEME_DESC','ENG','Seasonal theme applied to every screen. Auto follows the current season.'),
('W004','THEME_DESC','JPN','全画面に適用される季節テーマです。自動は現在の季節に従います。'),
('W004','THEME_AUTO','KOR','자동(계절)'),
('W004','THEME_AUTO','ENG','Auto (season)'),
('W004','THEME_AUTO','JPN','自動（季節）'),
('W004','THEME_SPRING','KOR','봄'),
('W004','THEME_SPRING','ENG','Spring'),
('W004','THEME_SPRING','JPN','春'),
('W004','THEME_SUMMER','KOR','여름'),
('W004','THEME_SUMMER','ENG','Summer'),
('W004','THEME_SUMMER','JPN','夏'),
('W004','THEME_AUTUMN','KOR','가을'),
('W004','THEME_AUTUMN','ENG','Autumn'),
('W004','THEME_AUTUMN','JPN','秋'),
('W004','THEME_WINTER','KOR','겨울'),
('W004','THEME_WINTER','ENG','Winter'),
('W004','THEME_WINTER','JPN','冬'),
-- 언어 마스터 등록 폼의 모달 전환에 따른 열기 버튼 라벨(등록 폼 제출 버튼은 기존 SUBMIT)
('W004','I18N_ADD','KOR','텍스트 등록'),
('W004','I18N_ADD','ENG','Add text'),
('W004','I18N_ADD','JPN','テキスト登録');
