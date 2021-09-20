package com.attendance.pro.dto;

import java.util.Date;

public class AttendanceDto extends BaseDto{

    /**
     * 
     */
    private static final long serialVersionUID = 5805014975808280181L;
    
    private Date attendanceDate;
    private Integer attendanceSeq;
    private Integer attendanceType;
    private Integer attendanceStatus;
    private Date attendanceRegDate;
    private Date attendanceUpdDate;
    private Date attendanceDelDate;
    private Integer attendanceUpdCnt;
    private Integer attendanceDelCnt;
    private String latitude;
    private String longitude;
    private String placeInfo;
    private String terminal;
    private String errorCd;
    private String errorMsg;
    private String remark;
    private UserDto userDto;
    
    public Date getAttendanceDate() {
        return attendanceDate;
    }
    public void setAttendanceDate(Date attendanceDate) {
        this.attendanceDate = attendanceDate;
    }
    public Integer getAttendanceSeq() {
        return attendanceSeq;
    }
    public void setAttendanceSeq(Integer attendanceSeq) {
        this.attendanceSeq = attendanceSeq;
    }
    public Integer getAttendanceType() {
        return attendanceType;
    }
    public void setAttendanceType(Integer attendanceType) {
        this.attendanceType = attendanceType;
    }
    public Integer getAttendanceStatus() {
        return attendanceStatus;
    }
    public void setAttendanceStatus(Integer attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }
    public Date getAttendanceRegDate() {
        return attendanceRegDate;
    }
    public void setAttendanceRegDate(Date attendanceRegDate) {
        this.attendanceRegDate = attendanceRegDate;
    }
    public Date getAttendanceUpdDate() {
        return attendanceUpdDate;
    }
    public void setAttendanceUpdDate(Date attendanceUpdDate) {
        this.attendanceUpdDate = attendanceUpdDate;
    }
    public Date getAttendanceDelDate() {
        return attendanceDelDate;
    }
    public void setAttendanceDelDate(Date attendanceDelDate) {
        this.attendanceDelDate = attendanceDelDate;
    }
    public Integer getAttendanceUpdCnt() {
        return attendanceUpdCnt;
    }
    public void setAttendanceUpdCnt(Integer attendanceUpdCnt) {
        this.attendanceUpdCnt = attendanceUpdCnt;
    }
    public Integer getAttendanceDelCnt() {
        return attendanceDelCnt;
    }
    public void setAttendanceDelCnt(Integer attendanceDelCnt) {
        this.attendanceDelCnt = attendanceDelCnt;
    }
    public String getLatitude() {
        return latitude;
    }
    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }
    public String getLongitude() {
        return longitude;
    }
    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }
    public String getPlaceInfo() {
        return placeInfo;
    }
    public void setPlaceInfo(String placeInfo) {
        this.placeInfo = placeInfo;
    }
    public String getTerminal() {
        return terminal;
    }
    public void setTerminal(String terminal) {
        this.terminal = terminal;
    }
    public String getErrorCd() {
        return errorCd;
    }
    public void setErrorCd(String errorCd) {
        this.errorCd = errorCd;
    }
    public String getErrorMsg() {
        return errorMsg;
    }
    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
    public String getRemark() {
        return remark;
    }
    public void setRemark(String remark) {
        this.remark = remark;
    }
    public UserDto getUserDto() {
        return userDto;
    }
    public void setUserDto(UserDto userDto) {
        this.userDto = userDto;
    }
    
    
    
}
