package com.attendance.pro.dao;

import java.util.HashMap;
import java.util.Map;

public class DaoManagement {
    
    //AdminScan은 데이터 확인용이므로 제외
    public static String[] Daos = {"LANGAGUE_MST","USER_MST"};
    private static String tableBase = " REGIST_USER : VARCHAR2(10) \"등록 유저\", "
            + "REGIST_DATE : DATE DEFAULT(SYSDATE) \"등록일\", "
            + "UPDATE_USER : VARCHAR2(10) \"수정 유저\", "
            + "UPDATE_DATE : DATE DEFAULT(SYSDATE) \"수정일\","
            + "UPDATE_CNT : NUMBER(10) DEFAULT(0) \"수정 횟수\","
            + "DEL_FLG : VARCHAR2(1) DEFAULT('0') \"삭제 플래그\" ";
    
        //컬럼 정의
    
    
    public static Map<String, String> getColumns(){
        Map<String, String>  columns = new HashMap<String, String>();
        //마스터 정보 등록
        /**
         * 언어 설정 마스터
         */
        String langaugeMaster = "{ WINDOW_ID : VARCHAR2(20) PK \"화면 명\", "
                                                + "MASTER_NAME : VARCHAR2(20) PK \"타임리프 명\", "
                                                + "LANG : VARCHAR2(5) PK \"언어\", "
                                                + "LANG_VALUE : VARCHAR2(1000) \"언어 값\", " + tableBase +"}";
        /**
         * TODO 회원 마스터
         */
        String userMaster = "{ USER_EMAIL : VARCHAR2(30) PK \"유저 이메일\", "
                + "USER_PWD : VARCHAR2(200) NOT NULL \"유저 비밀번호\"," 
                + "USER_NAME : VARCHAR2(50) \"유저 이름\"," 
                + "DEPART_CD : VARCHAR2(50) \"부서 코드\"," 
                + tableBase +"}";
        
        
        //마스터 정보 입력
        columns.put(Daos[0], langaugeMaster); //언어 설정 마스터
        columns.put(Daos[1], userMaster); //회원 마스터
        
        return columns;
    }

}
