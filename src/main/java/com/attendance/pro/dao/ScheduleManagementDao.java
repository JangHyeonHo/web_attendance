package com.attendance.pro.dao;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import com.attendance.pro.dto.ScheduleManagementDto;

@Mapper
@Repository
public interface ScheduleManagementDao {

    List<ScheduleManagementDto> getNowMonthSchedule(
            @Param("userCd")String userCd,
            @Param("nowDate") Date nowDate, 
            @Param("nextDate") Date nextDate);

    
}
