-- =========================================================
-- V73: 결재 도장 이미지 — 근태 마감 승인 시 근태 보고서(엑셀/PDF) 결재란에 자동 날인.
--  - stamp_image: 회사 도장 이미지(PNG/JPEG). 미업로드면 검은 원으로 대체.
--  - stamp_mime : 이미지 MIME(image/png|image/jpeg).
--  - stamp_size : 보고서 표시 크기 SMALL|MEDIUM|LARGE(레이아웃 붕괴 방지 상한 — 업로드 원본은 ≤300px·≤200KB).
--  도장은 그 달이 '마감 완료(APPROVED)'된 멤버의 보고서에만 찍힌다(승인 전엔 빈 결재란).
-- =========================================================
ALTER TABLE tenant_report_setting
    ADD COLUMN IF NOT EXISTS stamp_image MEDIUMBLOB NULL COMMENT '결재 도장 이미지(PNG/JPEG). NULL=검은 원 대체' AFTER pay_premium_enabled,
    ADD COLUMN IF NOT EXISTS stamp_mime  VARCHAR(30) NULL COMMENT '도장 이미지 MIME' AFTER stamp_image,
    ADD COLUMN IF NOT EXISTS stamp_size  VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' COMMENT '표시 크기 SMALL|MEDIUM|LARGE' AFTER stamp_mime;

-- 회사 설정(W020) 도장 업로드/크기 라벨 ------------------
INSERT IGNORE INTO language_master (window_id, lang_key, lang, lang_value) VALUES
('W020','STAMP_IMAGE','KOR','결재 도장 이미지'),        ('W020','STAMP_IMAGE','ENG','Approval stamp image'),  ('W020','STAMP_IMAGE','JPN','決裁印画像'),
('W020','STAMP_IMAGE_HINT','KOR','마감 승인 시 근태 보고서 결재란에 자동으로 찍힙니다. PNG·JPG, 최대 300×300px·200KB. 미등록 시 검은 원으로 대체됩니다.'),
('W020','STAMP_IMAGE_HINT','ENG','Auto-stamped on the report when a month is approved. PNG/JPG, up to 300×300px / 200KB. A black circle is used if none.'),
('W020','STAMP_IMAGE_HINT','JPN','締め承認時に勤怠報告書の決裁欄へ自動で押印されます。PNG・JPG、最大300×300px・200KB。未登録時は黒い円で代替。'),
('W020','STAMP_UPLOAD','KOR','이미지 선택'),            ('W020','STAMP_UPLOAD','ENG','Choose image'),         ('W020','STAMP_UPLOAD','JPN','画像を選択'),
('W020','STAMP_REMOVE','KOR','도장 삭제'),              ('W020','STAMP_REMOVE','ENG','Remove stamp'),         ('W020','STAMP_REMOVE','JPN','印を削除'),
('W020','STAMP_SIZE','KOR','도장 크기'),                ('W020','STAMP_SIZE','ENG','Stamp size'),             ('W020','STAMP_SIZE','JPN','印のサイズ'),
('W020','STAMP_SIZE_S','KOR','작게'),                   ('W020','STAMP_SIZE_S','ENG','Small'),                ('W020','STAMP_SIZE_S','JPN','小'),
('W020','STAMP_SIZE_M','KOR','보통'),                   ('W020','STAMP_SIZE_M','ENG','Medium'),               ('W020','STAMP_SIZE_M','JPN','中'),
('W020','STAMP_SIZE_L','KOR','크게'),                   ('W020','STAMP_SIZE_L','ENG','Large'),                ('W020','STAMP_SIZE_L','JPN','大'),
('W020','STAMP_NONE','KOR','등록된 도장 없음 (검은 원 사용)'),
('W020','STAMP_NONE','ENG','No stamp set (black circle used)'),
('W020','STAMP_NONE','JPN','登録された印なし（黒い円を使用）'),
('W020','STAMP_TOO_BIG','KOR','이미지가 너무 큽니다. 300×300px·200KB 이하로 등록하세요.'),
('W020','STAMP_TOO_BIG','ENG','Image too large. Use 300×300px / 200KB or smaller.'),
('W020','STAMP_TOO_BIG','JPN','画像が大きすぎます。300×300px・200KB以下にしてください。');
