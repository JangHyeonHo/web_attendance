package com.attendance.pro.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.attendance.pro.dto.UserDto;

/**
 * 테이블 USER_MST 참조용 DAO
 * @author jang
 *
 */
@Mapper 
public interface UserMasterDao {
    
    public UserDto getUser(@Param("userEmail") String userEmail,
            @Param("userPwd") String userPwd);

}
