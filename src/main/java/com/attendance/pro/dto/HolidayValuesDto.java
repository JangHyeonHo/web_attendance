package com.attendance.pro.dto;

public class HolidayValuesDto extends BaseDto {

    /**
     * 
     */
    private static final long serialVersionUID = -3003869335639101801L;
    
    private Integer HolidaySeq;
    private String HolidayName;
    private String HolidayComment;
    
    public Integer getHolidaySeq() {
        return HolidaySeq;
    }
    public void setHolidaySeq(Integer holidaySeq) {
        HolidaySeq = holidaySeq;
    }
    public String getHolidayName() {
        return HolidayName;
    }
    public void setHolidayName(String holidayName) {
        HolidayName = holidayName;
    }
    public String getHolidayComment() {
        return HolidayComment;
    }
    public void setHolidayComment(String holidayComment) {
        HolidayComment = holidayComment;
    }
    
    
}
