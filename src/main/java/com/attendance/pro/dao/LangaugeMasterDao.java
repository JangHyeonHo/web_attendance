package com.attendance.pro.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import com.attendance.pro.dto.LangaugeMasterDto;

@Mapper 
public interface LangaugeMasterDao {

    public List<LangaugeMasterDto> getAllLangs();
    
    
}
