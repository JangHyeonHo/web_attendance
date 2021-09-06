package com.attendance.pro.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.attendance.pro.dto.AttendanceDto;

@Mapper 
public interface AttendanceDao {

    /**
     * 현재 접속 유저의 오늘자 등록번호(seq)를 취득
     * @param userCd
     * @return
     */
    public Integer getAttendanceSeq(@Param("userCd") String userCd);

    /**
     * 유저의 출결 데이터를 등록
     * @param userCd
     * @return
     */
    public Integer registAttendance(AttendanceDto dto);
    
    /**
     * 현재 접속 유저의 오늘자 출근 데이터를 취득
     * @param userCd
     * @return
     */
    public AttendanceDto getNewestAttendance(@Param("userCd") String userCd);

    /**
     * 가장 최근의 출근확인용 데이터를 취득
     * @param userCd
     * @return
     */
    public AttendanceDto getNewestAttendData(String userCd);

}
