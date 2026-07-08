-- =========================================================
-- V2: 초기 관리자 계정
-- 이메일: admin@attendance.local / 비밀번호: Admin123!
-- 운영 배포시 반드시 비밀번호를 변경하거나 이 계정을 삭제할 것.
-- =========================================================

INSERT INTO users (email, password_hash, name, is_admin)
VALUES ('admin@attendance.local',
        '$2a$10$Ime9itLRTCozB3PD8DBk.uWmM23sRyhehEiYevRDgciOrF2SgmNp2',
        '관리자',
        TRUE);
