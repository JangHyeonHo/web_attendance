package com.attendance.pro;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 애플리케이션 컨텍스트 기동 테스트 — 전체 빈 그래프가 실제로 조립되는지(DI 배선) 검증한다.
 * 단위 테스트(Mockito)는 개별 빈만 검사하므로 생성자 다중정의·@Autowired 누락 같은
 * 배선 회귀를 잡지 못한다 → 이 테스트가 그 공백을 메운다.
 *
 * 기동 시 Flyway 마이그레이션이 실제 MariaDB 접속을 요구한다(DB_URL/DB_USERNAME/DB_PASSWORD).
 * 현재 별도 CI가 없고 테스트는 DB가 있는 환경에서만 실행되므로 상시 활성화한다.
 * DB 없는 CI를 도입하면 이 클래스만 프로파일/조건으로 분리할 것.
 */
@SpringBootTest
@ActiveProfiles("test") //base 설정의 개발 기본 암호화 키를 FieldCipher가 허용하도록(dev/test/local만 허용)
class WebAttendanceApplicationTests {

	@Test
	void contextLoads() {
	}

}
