-- =========================================================
-- V66: 반복 근무 패턴(요일별 시간 + N주 주기) — 매월 재입력 없이 자동 반복(#13)
--  - 사람당 활성 패턴 1개. cycle_weeks=1이면 요일맵(케이스 3), N이면 N주 주기(케이스 4).
--  - anchor_monday: 주기 0주차 기준 월요일(주차 계산 원점).
--  - 슬롯은 (주차, 요일) → 근무(시업/종업/야간) 또는 휴무(off). 모든 스케줄은 시작시간 기준.
--  우선순위(해석): 공휴일 > work_schedule 일자 오버라이드(로타) > 이 패턴 > 개인 기본값 > 상수.
-- =========================================================
CREATE TABLE schedule_pattern (
    pattern_id    BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    cycle_weeks   TINYINT      NOT NULL DEFAULT 1,
    anchor_monday DATE         NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (pattern_id),
    UNIQUE KEY uk_pattern_user (tenant_id, user_id),
    CONSTRAINT fk_pattern_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
    CONSTRAINT fk_pattern_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE schedule_pattern_slot (
    pattern_id       BIGINT  NOT NULL,
    week_index       TINYINT NOT NULL,           -- 0..cycle_weeks-1
    day_of_week      TINYINT NOT NULL,           -- 1..7 (월..일)
    off              BOOLEAN NOT NULL DEFAULT FALSE,
    start_time       TIME    NULL,
    end_time         TIME    NULL,
    crosses_midnight BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (pattern_id, week_index, day_of_week),
    CONSTRAINT fk_pattern_slot FOREIGN KEY (pattern_id)
        REFERENCES schedule_pattern (pattern_id) ON DELETE CASCADE
);

-- 패턴 편집기 라벨(W009)
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W009','PATTERN_EDIT','KOR','반복 패턴 편집'),   ('W009','PATTERN_EDIT','ENG','Edit repeating pattern'), ('W009','PATTERN_EDIT','JPN','繰り返しパターン編集'),
('W009','PATTERN_TITLE','KOR','반복 근무 패턴'),  ('W009','PATTERN_TITLE','ENG','Repeating work pattern'), ('W009','PATTERN_TITLE','JPN','繰り返し勤務パターン'),
('W009','PATTERN_HINT','KOR','요일별 시간과 주기(격주 등)를 지정하면 매월 자동 반복됩니다. 특정일 예외는 월 로타에서.'),
('W009','PATTERN_HINT','ENG','Set per-weekday hours and a cycle (e.g. biweekly) to repeat automatically each month. Per-date exceptions live in the monthly rota.'),
('W009','PATTERN_HINT','JPN','曜日別の時間と周期（隔週など）を指定すると毎月自動で繰り返します。特定日の例外は月間ロタで。'),
('W009','CYCLE_WEEKS','KOR','반복 주기(주)'),     ('W009','CYCLE_WEEKS','ENG','Cycle (weeks)'),  ('W009','CYCLE_WEEKS','JPN','周期（週）'),
('W009','WEEK_N','KOR','{n}주차'),                ('W009','WEEK_N','ENG','Week {n}'),            ('W009','WEEK_N','JPN','{n}週目'),
('W009','PATTERN_NONE','KOR','반복 패턴 없음(기본 스케줄 사용)'), ('W009','PATTERN_NONE','ENG','No repeating pattern (uses fixed schedule)'), ('W009','PATTERN_NONE','JPN','繰り返しパターンなし（固定スケジュール使用）'),
('W009','PATTERN_CLEAR','KOR','패턴 삭제'),       ('W009','PATTERN_CLEAR','ENG','Clear pattern'), ('W009','PATTERN_CLEAR','JPN','パターン削除'),
('W009','TEAM_BOARD','KOR','팀 로타 보드'),       ('W009','TEAM_BOARD','ENG','Team rota board'), ('W009','TEAM_BOARD','JPN','チームロタボード');
