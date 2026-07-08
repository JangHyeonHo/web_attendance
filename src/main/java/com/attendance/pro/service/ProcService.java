package com.attendance.pro.service;

import java.util.Map;

/**
 * 화면 단위 프로세스 처리 서비스의 공통 계약.
 * (구 BaseServiceImpl — 인터페이스가 Impl로 명명되어 있던 것을 개명)
 */
public interface ProcService {

    /**
     * 화면에서 요청한 프로세스를 처리한다.
     *
     * @param data       요청 받은 데이터
     * @param resData    반환할 데이터
     * @param windowData 화면 값(언어설정)
     * @param props      기타값(유저 코드 등)
     * @return 처리 결과가 반영된 resData
     */
    Map<String, Object> proc(Map<String, Object> data,
            Map<String, Object> resData, Map<String, String> windowData,
            String... props);

}
