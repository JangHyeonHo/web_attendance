package com.attendance.pro.dto;

import java.util.Date;

public class ScheduleManagementDto extends BaseDto {

    /**
     * 
     */
    private static final long serialVersionUID = 1054124028162243917L;

    //USER_CD, SCHEDULE_CD, SCHEDULE_DATE, SCHEDULE_START_TIME, SCHEDULE_END_TIME,
    //HOLIDAY_SEQ, ADMIN_APPROVE
    private String scheduleCd;
    private Date scheduleDate;
    private String scheduleStartTime;
    private String scheduleEndTime;
    private Integer adminApprove;
    private HolidayValuesDto holidayValuesDto;
    private UserDto userDto;
    
    public String getScheduleCd() {
        return scheduleCd;
    }
    public void setScheduleCd(String scheduleCd) {
        this.scheduleCd = scheduleCd;
    }
    public Date getScheduleDate() {
        return scheduleDate;
    }
    public void setScheduleDate(Date scheduleDate) {
        this.scheduleDate = scheduleDate;
    }
    public String getScheduleStartTime() {
        return scheduleStartTime;
    }
    public void setScheduleStartTime(String scheduleStartTime) {
        this.scheduleStartTime = scheduleStartTime;
    }
    public String getScheduleEndTime() {
        return scheduleEndTime;
    }
    public void setScheduleEndTime(String scheduleEndTime) {
        this.scheduleEndTime = scheduleEndTime;
    }
    public Integer getAdminApprove() {
        return adminApprove;
    }
    public void setAdminApprove(Integer adminApprove) {
        this.adminApprove = adminApprove;
    }
    public HolidayValuesDto getHolidayValuesDto() {
        return holidayValuesDto;
    }
    public void setHolidayValuesDto(HolidayValuesDto holidayValuesDto) {
        this.holidayValuesDto = holidayValuesDto;
    }
    public UserDto getUserDto() {
        return userDto;
    }
    public void setUserDto(UserDto userDto) {
        this.userDto = userDto;
    }
    
    
}
