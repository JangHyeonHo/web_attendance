package com.attendance.pro.dao;

import java.util.HashMap;
import java.util.Map;

public class DaoManagement {
    
    //AdminScan은 데이터 확인용이므로 제외
    public static String[] Daos = {"LANGAGUE_MST"};
    private static String tableBase = " REGIST_USER : VARCHAR2(10) \"등록 유저\", "
            + "REGIST_DATE : DATE default(SYSDATE) \"등록일\", "
            + "UPDATE_USER : VARCHAR2(10) \"수정 유저\", "
            + "UPDATE_DATE : DATE default(SYSDATE) \"수정일\","
            + "UPDATE_CNT : NUMBER(10) default(0) \"수정 횟수\","
            + "DEL_FLG : VARCHAR2(1) default('0') \"삭제 플래그\" ";
    
    //컬럼 정의
    
    
    public static Map<String, String> getColumns(){
        Map<String, String>  columns = new HashMap<String, String>();
        //마스터 정보 등록
        String langaugeMaster = "{ WINDOW_ID : VARCHAR2(20) pk \"화면 명\", "
                                                + "MASTER_NAME : VARCHAR2(20) pk \"타임리프 명\", "
                                                + "LANG : VARCHAR2(5) pk \"언어\", "
                                                + "LANG_VALUE : VARCHAR2(1000)  \"언어 값\", " + tableBase +"}";
        
        
        //마스터 정보 입력
        columns.put("LANGAGUE_MST", langaugeMaster); //언어 설정 마스터
        
        return columns;
    }

}
