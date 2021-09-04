package com.attendance.pro.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.attendance.pro.dto.LanguageMasterDto;

/**
 * 테이블 LANGAUGE_MST 참조용 DAO
 * @author jang
 *
 */
@Mapper 
public interface LanguageMasterDao {

    public List<LanguageMasterDto> getAllLangs(@Param("lang") String lang,
            @Param("window") String window);

    public Integer insertData(LanguageMasterDto dto);
    
    
}
