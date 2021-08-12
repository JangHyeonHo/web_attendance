package com.attendance.pro.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import com.attendance.pro.dto.LangaugeMasterDto;

/**
 * 테이블 LANGAUGE_MST 참조용 DAO
 * @author jang
 *
 */
@Mapper 
public interface LangaugeMasterDao {

    public List<LangaugeMasterDto> getAllLangs();
    
    
}
