# 08. 배포 갱신·롤백

> [인덱스](README.md)

## 갱신 절차 (다운타임 수 초)

```bash
cd /home/ubuntu/web_attendance
git pull

# 백엔드 — 롤백용으로 직전 jar을 보관하고 교체
./mvnw -q package -DskipTests
sudo cp /opt/attendance/app.jar /opt/attendance/app.jar.prev
sudo cp target/*.jar /opt/attendance/app.jar

# 프론트
cd frontend && npm ci && npm run build
sudo rsync -a --delete dist/ /var/www/attendance/

# 재기동 — 새 Flyway 마이그레이션이 있으면 기동 시 자동 적용
sudo systemctl restart attendance
journalctl -u attendance -n 30 --no-pager   # Started ... 확인
```

- 테스트를 서버에서 돌리지 않는 이유: 테스트는 PR 단계에서 이미 통과한 코드만 main에
  들어온다는 전제. 서버 빌드는 `-DskipTests`로 시간을 아낀다.
- 프론트만 바뀐 갱신은 재기동 불요(rsync까지만).

## 롤백

```bash
sudo cp /opt/attendance/app.jar.prev /opt/attendance/app.jar
sudo systemctl restart attendance
```

**단, 이미 적용된 DB 마이그레이션은 되돌리지 않는다.**
이 프로젝트는 전방 수정 원칙 — 마이그레이션에 문제가 있으면 그것을 고치는 **새 V버전**을
추가해서 앞으로 나아간다(적용된 버전 파일 수정·삭제 금지. Flyway 체크섬 불일치로 기동이 거부된다).
따라서 "마이그레이션을 포함한 갱신"의 롤백은 jar만 되돌려서는 안 되고, 스키마 변경이
구버전과 호환인지 확인한 후 판단한다.

## 갱신 전 확인 습관

- [ ] 새 마이그레이션 유무 확인: `git log --stat -- src/main/resources/db/migration | head`
- [ ] 마이그레이션이 있으면: 직전 백업이 오늘 것인지 확인([09](09-backup-restore.md)) 후 갱신
- [ ] 갱신 직후: [07](07-initial-setup.md)의 검증 시나리오 중 로그인·스탬프·조회 3개만 빠르게
