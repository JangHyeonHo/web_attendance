-- =========================================================
-- V1: 초기 스키마 (MariaDB)
-- =========================================================

-- 회원
CREATE TABLE users (
    user_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '유저 ID',
    email         VARCHAR(100) NOT NULL COMMENT '이메일(로그인 ID)',
    password_hash VARCHAR(100) NOT NULL COMMENT '비밀번호 해시(BCrypt)',
    name          VARCHAR(50)  NOT NULL COMMENT '이름',
    depart_cd     VARCHAR(50)  NULL COMMENT '부서 코드',
    is_admin      BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '관리자 여부',
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '삭제 플래그',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email)
) COMMENT '회원 마스터';

-- 출결 스탬프 (1=출근, 2=퇴근, 3=조퇴, 4=휴식)
CREATE TABLE attendance (
    attendance_id BIGINT        NOT NULL AUTO_INCREMENT COMMENT '출결 ID',
    user_id       BIGINT        NOT NULL COMMENT '유저 ID',
    type          TINYINT       NOT NULL COMMENT '출결 타입(1출근 2퇴근 3조퇴 4휴식)',
    status        TINYINT       NOT NULL DEFAULT 0 COMMENT '상태(휴식: 0=시작 1=종료)',
    stamped_at    DATETIME      NOT NULL COMMENT '스탬프 시각',
    latitude      DECIMAL(10,7) NULL COMMENT '위도',
    longitude     DECIMAL(10,7) NULL COMMENT '경도',
    place_info    VARCHAR(200)  NULL COMMENT '장소 정보',
    terminal      VARCHAR(100)  NULL COMMENT '단말 정보',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    PRIMARY KEY (attendance_id),
    KEY idx_attendance_user_stamped (user_id, stamped_at),
    CONSTRAINT fk_attendance_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT '출결 스탬프';

-- 출결 체크 토큰 (체크→확정 사이의 변조 방지)
CREATE TABLE attendance_check (
    token        CHAR(36)  NOT NULL COMMENT '확인 토큰(UUID)',
    user_id      BIGINT    NOT NULL COMMENT '유저 ID',
    payload_hash CHAR(64)  NOT NULL COMMENT '체크 시점 요청 데이터의 SHA-256 해시',
    confirm_code TINYINT   NULL COMMENT '확인 코드(덮어쓰기 확인 등)',
    created_at   DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    PRIMARY KEY (token),
    KEY idx_attendance_check_user (user_id),
    CONSTRAINT fk_attendance_check_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT '출결 체크 토큰';

-- 근무 스케쥴 (일자별 근무시간 오버라이드, 미등록 일자는 기본 09:00~18:00)
CREATE TABLE work_schedule (
    schedule_id BIGINT  NOT NULL AUTO_INCREMENT COMMENT '스케쥴 ID',
    user_id     BIGINT  NOT NULL COMMENT '유저 ID',
    work_date   DATE    NOT NULL COMMENT '근무일',
    start_time  TIME    NULL COMMENT '시업 시각',
    end_time    TIME    NULL COMMENT '종업 시각',
    holiday     BOOLEAN NOT NULL DEFAULT FALSE COMMENT '개인 휴일 여부(휴가 등)',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    PRIMARY KEY (schedule_id),
    UNIQUE KEY uk_work_schedule_user_date (user_id, work_date),
    CONSTRAINT fk_work_schedule_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT '근무 스케쥴';

-- 공휴일 (전사 공통)
CREATE TABLE holiday (
    holiday_date DATE         NOT NULL COMMENT '공휴일',
    name         VARCHAR(100) NOT NULL COMMENT '명칭',
    PRIMARY KEY (holiday_date)
) COMMENT '공휴일';

-- 언어 마스터 (화면 그룹 + 키 + 언어 -> 텍스트)
CREATE TABLE language_master (
    language_id BIGINT        NOT NULL AUTO_INCREMENT COMMENT '언어 ID',
    window_id   VARCHAR(20)   NOT NULL COMMENT '화면(그룹) ID',
    lang_key    VARCHAR(50)   NOT NULL COMMENT '텍스트 키',
    lang        VARCHAR(5)    NOT NULL COMMENT '언어(KOR/ENG)',
    lang_value  VARCHAR(1000) NOT NULL COMMENT '텍스트 값',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (language_id),
    UNIQUE KEY uk_language_master (window_id, lang_key, lang)
) COMMENT '언어 마스터';
