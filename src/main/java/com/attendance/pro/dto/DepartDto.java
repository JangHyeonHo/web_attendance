package com.attendance.pro.dto;

import java.util.Date;

public class DepartDto extends BaseDto {

    /**
     * departCd = PK
     */
    private static final long serialVersionUID = 2410962538442541783L;
    
    private String departCd;                            //부서 코드
    private String departName;                       //부서 명
    private Integer departRank;                      //부서 등급
    private Date departRegDate;                    //부서 등록일
    private Date departDelDate;                     //부서 삭제일
    private String departStatus;                      //부서 상태
    
    public String getDepartCd() {
        return departCd;
    }
    public void setDepartCd(String departCd) {
        this.departCd = departCd;
    }
    public String getDepartName() {
        return departName;
    }
    public void setDepartName(String departName) {
        this.departName = departName;
    }
    public Integer getDepartRank() {
        return departRank;
    }
    public void setDepartRank(Integer departRank) {
        this.departRank = departRank;
    }
    public Date getDepartRegDate() {
        return departRegDate;
    }
    public void setDepartRegDate(Date departRegDate) {
        this.departRegDate = departRegDate;
    }
    public Date getDepartDelDate() {
        return departDelDate;
    }
    public void setDepartDelDate(Date departDelDate) {
        this.departDelDate = departDelDate;
    }
    public String getDepartStatus() {
        return departStatus;
    }
    public void setDepartStatus(String departStatus) {
        this.departStatus = departStatus;
    }
    
}
