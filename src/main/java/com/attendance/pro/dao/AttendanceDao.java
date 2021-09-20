package com.attendance.pro.dao;

import java.util.Date;
import java.util.List;

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
    public Integer getAttendanceSeq(@Param("userCd") String userCd) throws Exception;

    /**
     * 유저의 출결 데이터를 등록
     * @param userCd
     * @return
     */
    public Integer registAttendance(AttendanceDto dto) throws Exception;
    
    /**
     * 현재 접속 유저의 가장 최근의 출퇴근 데이터를 취득
     * @param userCd
     * @return
     */
    public AttendanceDto getNewestAttendance(@Param("userCd") String userCd) throws Exception;

    /**
     * 현재 접속 유저의 가장 최근의 출근 데이터를 취득
     * @param userCd
     * @return
     */
    public AttendanceDto getNewestAttendData(@Param("userCd") String userCd) throws Exception;

    /**
     * 유저의 한달 입력 데이터를 취득
     * @param userCd
     * @param nowDate
     * @param nextDate
     * @return
     */
    public List<AttendanceDto> getNowMonthStamp(@Param("userCd") String userCd,
            @Param("nowDate") Date nowDate,
            @Param("nextDate") Date nextDate);

    /**
     * 재 출근시 현 날짜의 이전 데이터들을 삭제처리
     * @param dto
     * @return
     */
    public Integer reAttendingToDeleteStatus(AttendanceDto dto);

}
