package com.attendance.pro.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;

/**
 * 화면 관리
 * 화면 암호화를 위해 별도의 명칭을 담을 예정
 * @author jang
 *
 */
@Controller
public class WindowManagement {
    
    protected Map<String,Boolean> confirmPath = setConfirmPath();
    
    //화면 아이디
    public static String Index = "W000";
    public static String Login = "W001"; 
    public static String Logout = "W002"; 
    public static String SignUp = "W003"; 
    public static String Admin = "W004"; 
    public static String Main = "W005"; 
    
    public boolean isConfirmPath(String windowId) {
        return confirmPath.get(windowId);
    }
    
    /**
     * 로그인이 필요한 화면은 true, 필요없는 화면은 false로 대처함
     * @return
     */
    private static Map<String,Boolean> setConfirmPath() {
        Map<String,Boolean> path = new HashMap<String, Boolean>();
        path.put(Index, true);
        path.put(Login, false);
        path.put(Logout, false);
        path.put(SignUp, false);
        path.put(Admin, false); //test용 false -> 끝나면 true로 바꿈
        path.put(Main, false);
        return path;
    }
    
}
