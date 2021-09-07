package com.attendance.pro.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LogicServiceDao {

    /**
     * 서비스 에러 관리
     * @param result
     * @param resData
     * @param checkService
     * @param userCd
     * @param errorCd
     * @param errorMsg
     * @return
     */
    Integer insertService(@Param("result") String result,
            @Param("resData") String resData,
            @Param("checkService") String checkService,
            @Param("userCd") String userCd,
            @Param("status") String status,
            @Param("errorCd") String errorCd,
            @Param("errorMsg") String errorMsg
            );

    /**
     * 체크 로직을 취득
     * @param result
     * @param userCd
     * @return
     */
    String getCheckLogic(@Param("result")String result, 
            @Param("userCd")String userCd);

    /**
     * 서비스 관리에 데이터 쌓이는 것을 방지하기 위하여 일주일간의 데이터를 서버 기동시 삭제
     * 원래라면 배치를 만들어서 자동으로 삭제되게 해야하나 배치를 가동시킬려면 DB서버를 상시 기동시켜야하므로
     * 이런식으로 대체함
     */
    Integer successDeleteToWeek();

}
