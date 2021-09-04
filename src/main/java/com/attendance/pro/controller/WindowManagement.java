package com.attendance.pro.controller;

import static com.attendance.pro.other.CodeMap.isEqualMultyisOne;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Controller;

import com.attendance.pro.other.CodeMap;

/**
 * 화면 관리
 * 화면 암호화를 위해 별도의 명칭을 담을 예정
 * @author jang
 *
 */
@Controller
public class WindowManagement {
    
    private static Map<String,Map<String, String>> kor = null;
    private static Map<String,Map<String, String>> eng = null;
    
    protected Map<String,Boolean> confirmPath = setConfirmPath();
    
    //화면 아이디
    public static String Default = "W999";
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
        path.put(Default, false);
        return path;
    }
    
    //언어설정
    public static Map<String, String> getLangOfValueMapping(String windowId, Locale locale) {
        if(isEqualMultyisOne(locale, Locale.KOREAN, Locale.KOREA)) {
            return kor.get(windowId);
        } else if(isEqualMultyisOne(locale, Locale.JAPANESE, Locale.JAPAN)) {
            //현호 한정(해외 작업때문에)
            return kor.get(windowId);
        } else {
            return eng.get(windowId);
        }
    }

    public static Map<String,Map<String, String>> getKor() {
        return kor;
    }
    public static void setKor(Map<String,Map<String, String>> kor) {
        WindowManagement.kor = kor;
    }
    public static Map<String,Map<String, String>> getEng() {
        return eng;
    }
    public static void setEng(Map<String,Map<String, String>> eng) {
        WindowManagement.eng = eng;
    }
    public static void addKor(String winId, String key, String value) {
        if(kor.get(winId)==null) {
            kor.put(winId, new HashMap<String, String>());
        }
        kor.get(winId).put(key, value);
    }
    public static void addEng(String winId, String key, String value) {
        if(eng.get(winId)==null) {
            eng.put(winId, new HashMap<String, String>());
        }
        eng.get(winId).put(key, value);
    }

    public static List<String> getAllWindowId() {
        List<String> allId = new ArrayList<String>();
        allId.add(Index);
        allId.add(Login);
        allId.add(Logout);
        allId.add(SignUp);
        allId.add(Admin);
        allId.add(Main);
        allId.add(Default);
        return allId;
    }

    public static List<String> getAllLangs() {
        List<String> allLangs = new ArrayList<String>();
        allLangs.add(CodeMap.Korean);
        allLangs.add(CodeMap.English);
        return allLangs;
    }
    
    
}
