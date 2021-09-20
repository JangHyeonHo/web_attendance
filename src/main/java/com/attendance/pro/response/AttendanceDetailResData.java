package com.attendance.pro.response;

public class AttendanceDetailResData extends BaseResponseData {

    /**
     * 
     */
    private static final long serialVersionUID = -5192539344256433110L;
    
    private String fixScheduleIn; //고정 스케쥴
    private String fixScheduleOut; //고정 스케쥴
    private boolean isHoliday; //휴일인지?
    private String stampIn; //입력 스케쥴
    private String stampOut; //입력 스케쥴
    private String remark; //비고
    
    public String getFixScheduleIn() {
        return fixScheduleIn;
    }
    public void setFixScheduleIn(String fixScheduleIn) {
        this.fixScheduleIn = fixScheduleIn;
    }
    public String getFixScheduleOut() {
        return fixScheduleOut;
    }
    public void setFixScheduleOut(String fixScheduleOut) {
        this.fixScheduleOut = fixScheduleOut;
    }
    public boolean isHoliday() {
        return isHoliday;
    }
    public void setHoliday(boolean isHoliday) {
        this.isHoliday = isHoliday;
    }
    public String getStampIn() {
        return stampIn;
    }
    public void setStampIn(String stampIn) {
        this.stampIn = stampIn;
    }
    public String getStampOut() {
        return stampOut;
    }
    public void setStampOut(String stampOut) {
        this.stampOut = stampOut;
    }
    public String getRemark() {
        return remark;
    }
    public void setRemark(String remark) {
        this.remark = remark;
    }

}
