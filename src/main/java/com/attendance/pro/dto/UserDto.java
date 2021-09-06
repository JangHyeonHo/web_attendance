package com.attendance.pro.dto;

import java.util.Date;

public class UserDto extends BaseDto {

    /**
     * 
     */
    private static final long serialVersionUID = 4968030993703808330L;
    /**
     * userCd = PK
     */
    private String userCd;                             //유저 코드
    private String userEmail;                        //유저 이메일
    private String userPwd;                          //유저 비밀번호
    private String userName;                       //유저 명
    private Integer userRank;                      //유저 등급
    private Date userRegDate;                    //유저 등록일
    private Date userDelDate;                     //유저 삭제일
    private String userStatus;                      //유저 상태
    private DepartDto departDto;                //부서
     
    public String getUserCd() {
        return userCd;
    }
    public void setUserCd(String userCd) {
        this.userCd = userCd;
    }
    public String getUserEmail() {
        return userEmail;
    }
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
    public String getUserPwd() {
        return userPwd;
    }
    public void setUserPwd(String userPwd) {
        this.userPwd = userPwd;
    }
    public String getUserName() {
        return userName;
    }
    public void setUserName(String userName) {
        this.userName = userName;
    }
    public Integer getUserRank() {
        return userRank;
    }
    public void setUserRank(Integer userRank) {
        this.userRank = userRank;
    }
    public Date getUserRegDate() {
        return userRegDate;
    }
    public void setUserRegDate(Date userRegDate) {
        this.userRegDate = userRegDate;
    }
    public Date getUserDelDate() {
        return userDelDate;
    }
    public void setUserDelDate(Date userDelDate) {
        this.userDelDate = userDelDate;
    }
    public String getUserStatus() {
        return userStatus;
    }
    public void setUserStatus(String userStatus) {
        this.userStatus = userStatus;
    }
    public DepartDto getDepartDto() {
        return departDto;
    }
    public void setDepartDto(DepartDto departDto) {
        this.departDto = departDto;
    }
    
    
}
