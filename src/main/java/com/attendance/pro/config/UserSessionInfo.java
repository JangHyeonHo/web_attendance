package com.attendance.pro.config;

import java.io.Serializable;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
@Scope(value=WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserSessionInfo implements Serializable {
    
    /**
     * 
     */
    private static final long serialVersionUID = -4973990853064521029L;
    
    
    private String userName = "";
    private boolean isLogin = false;

    public String getUserName() {
        return userName;
    }
    public void setUserName(String userName) {
        this.userName = userName;
    }
    public boolean isLogin() {
        return isLogin;
    }
    public void setLogin(boolean isLogin) {
        this.isLogin = isLogin;
    }
    
    

}
