package com.attendance.pro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

//@EnableAsync: 비밀번호 재설정 메일을 응답과 분리 발송(응답 시간 = 계정 존재 오라클 차단)
@SpringBootApplication
@EnableAsync
public class WebAttendanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebAttendanceApplication.class, args);
	}

}
