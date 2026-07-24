# 10. 백업·복원·암호화 키 보관

> [인덱스](README.md)

## 백업 (매일, cron)

`/etc/cron.daily/attendance-backup` (chmod 700):

```sh
#!/bin/sh
mysqldump --single-transaction attendance | gzip > /var/backups/attendance-$(date +%F).sql.gz
find /var/backups -name 'attendance-*.sql.gz' -mtime +14 -delete
```

- `--single-transaction`: 서비스 무중단으로 일관된 스냅샷을 뜬다(InnoDB 전제).
- 보존 14일. 서버 밖 보관(S3 등)을 추가하면 서버 소실에도 대비된다 —
  테스트 단계에서는 선택, 운영에서는 필수.

## ★암호화 키(APP_CRYPTO_KEY) 보관 — 백업의 반쪽★

민감 필드는 DB 안에서 AES-256-GCM으로 암호화돼 있다. 그래서:

- **키 없이는 백업을 복원해도 암호화 필드를 읽을 수 없다** — 키를 잃으면 그 데이터는 영구 소실.
- **키와 백업이 함께 유출되면 전부 읽힌다** — 같은 장소에 두지 않는다.

규칙: `/etc/attendance/attendance.env`의 키 값을 **백업 파일과 다른 장소**
(예: 개인 비밀번호 관리자)에 별도 기록해 둔다. 키를 바꾸면 기존 암호문을 읽을 수 없으므로
키 로테이션은 별도 마이그레이션 계획 없이 하지 않는다.

## 복원

```bash
sudo systemctl stop attendance
zcat /var/backups/attendance-<날짜>.sql.gz | mysql attendance
sudo systemctl start attendance
```

- 다른 서버로 복원할 때는 03(DB 생성)·04(환경변수 — **같은 APP_CRYPTO_KEY**)를 먼저 갖춘다.
- Flyway 이력(flyway_schema_history)도 덤프에 포함되므로 복원 후 기동하면 그 시점 이후의
  마이그레이션만 이어서 적용된다.

## 복원 리허설 — ★백업은 복원해 본 것만 백업이다★

공개 전에 최소 1회: 백업 파일을 로컬(또는 임시 인스턴스)에 복원 → 로그인·출결 조회가
되는지 확인. 이걸 해 보지 않은 백업은 "아마 되겠지" 파일일 뿐이다.

## 점검

- [ ] `/var/backups/`에 오늘 날짜 파일이 생겼는지 (cron 첫 실행 후)
- [ ] 파일 크기가 0이 아닌지 (`ls -lh /var/backups/`)
- [ ] 복원 리허설 1회 완료
- [ ] APP_CRYPTO_KEY를 서버 밖 별도 장소에 기록했는지
