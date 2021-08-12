package com.attendance.pro.dto;

public class UserDto extends BaseDto {

    /**
     * 
     */
    private static final long serialVersionUID = 4968030993703808330L;
    /**
     * userEmail = PK
     */
    private String userEmail; //유저 이메일
    private String userPwd;   //유저 비밀번호
    private String userName; //유저명
    
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
    
    
}
