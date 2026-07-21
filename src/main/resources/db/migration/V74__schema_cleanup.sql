-- =========================================================
-- V74: 스키마 정리(리뷰 반영)
--  ① tenant_seat_usage 제거 — 좌석 추적이 seat_change_event(변동 이벤트 로그 재생) 방식으로 대체되어
--     더 이상 읽거나 쓰지 않는 잔재 테이블(코드 참조 0). 중복 표현 제거.
--  ② attendance_close FK 보강 — 다른 근태 테이블(attendance·work_schedule)과 동일하게
--     tenant/user 참조 무결성을 건다(approver_id는 결재자, nullable FK).
--     requested_by는 '멤버 본인 신청' 규약상 항상 user_id와 동일 → 중복 컬럼 제거.
-- =========================================================

-- ① 죽은 테이블 제거
DROP TABLE IF EXISTS tenant_seat_usage;

-- ② attendance_close 정리(중복 컬럼 제거 + FK 3종). user_id/approver_id FK는 MariaDB가
--    참조 인덱스를 자동 생성하고, tenant_id는 idx_attendance_close_pending(tenant_id, status)를 재사용한다.
ALTER TABLE attendance_close
    DROP COLUMN requested_by,
    ADD CONSTRAINT fk_attendance_close_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenant(tenant_id),
    ADD CONSTRAINT fk_attendance_close_user     FOREIGN KEY (user_id)     REFERENCES users(user_id),
    ADD CONSTRAINT fk_attendance_close_approver FOREIGN KEY (approver_id) REFERENCES users(user_id);
