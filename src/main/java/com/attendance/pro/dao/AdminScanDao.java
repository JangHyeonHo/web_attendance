package com.attendance.pro.dao;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminScanDao {

    /**
     * 해당 테이블이 존재하는지 확인 존재하면 값을 리턴
     * @param tableName
     * @return 1
     */
    String isTableExisting(@Param("tableName") String tableName);
    
    /**
     * 테이블 생성 로직
     * @param table
     * @param values
     * @return 없음
     */
    Integer createTable(@Param("table") String table, 
            @Param("values") String values);
    
    /**
     * 코멘트 붙이는 로직
     * @param comment
     * @return
     */
    Integer commentOn(@Param("comment") String comment);
    
    /**
     * 테이블 칼럼 취득 로직
     * @param table
     * @return List<String>
     */
    List<String> getTableColumns(@Param("table") String table);
    
    /**
     * 테이블 칼럼 데이터 취득 로직
     * @param table
     * @return List<String>
     */
    List<Map<String, String>> getTableInformations(@Param("table") String table);
    
    /**
     * 테이블 데이터 취득 로직
     * @param table
     * @param string
     * @return
     */
    List<Map<String, Object>> getTableDatas(@Param("table") String table, 
            @Param("columns")String columns);

    /**
     * 테이블 칼럼 변경 로직
     * @param alterNotNull
     * @return
     */
    Integer alterTable(String alterNotNull);

}
