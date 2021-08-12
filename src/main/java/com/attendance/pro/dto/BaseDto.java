package com.attendance.pro.dto;

import java.io.Serializable;
import java.util.Date;

public class BaseDto implements Serializable {
    
    /**
     * 
     */
    private static final long serialVersionUID = 3837876586774989328L;
    
    private String registUser;      //등록자
    private Date registDate;        //등록일자
    private String updateUser;     //수정자
    private Date updateDate;       //수정일자
    private int updateCnt;            //수정 횟수
    private String delFlg;              //삭제 플래그
    
    public String getRegistUser() {
        return registUser;
    }
    public void setRegistUser(String registUser) {
        this.registUser = registUser;
    }
    public Date getRegistDate() {
        return registDate;
    }
    public void setRegistDate(Date registDate) {
        this.registDate = registDate;
    }
    public String getUpdateUser() {
        return updateUser;
    }
    public void setUpdateUser(String updateUser) {
        this.updateUser = updateUser;
    }
    public Date getUpdateDate() {
        return updateDate;
    }
    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }
    public int getUpdateCnt() {
        return updateCnt;
    }
    public void setUpdateCnt(int updateCnt) {
        this.updateCnt = updateCnt;
    }
    public String getDelFlg() {
        return delFlg;
    }
    public void setDelFlg(String delFlg) {
        this.delFlg = delFlg;
    }
    
    

}
