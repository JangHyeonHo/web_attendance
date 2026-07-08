package com.attendance.pro;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 애플리케이션 컨텍스트 기동 테스트.
 * 기동시 초기화(언어 마스터 적재)가 실제 Oracle DB 접속을 필요로 하므로
 * DB가 없는 CI 환경에서는 비활성화한다. 로컬에서 DB를 띄운 뒤 @Disabled를 제거하고 실행한다.
 */
@Disabled("실행에 Oracle DB 접속(DB_URL/DB_USERNAME/DB_PASSWORD)이 필요")
@SpringBootTest
class WebAttendanceApplicationTests {

	@Test
	void contextLoads() {
	}

}
