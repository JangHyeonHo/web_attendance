package com.attendance.pro.controller;

import static com.attendance.pro.other.CodeMap.RES;
import static com.attendance.pro.other.CodeMap.SUCCESS;
import static com.attendance.pro.other.CodeMap.isEqual;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.config.UserSessionInfo;
import com.attendance.pro.service.UserManagementService;

/**
 * 메인 루트 컨트롤러
 * @author jang
 *
 */
@RestController
public class RootController extends WindowManagement{
    
    @Resource
    private UserSessionInfo userInfo;
    
    @Autowired
    private UserManagementService userManagementService = null;
    
    private Logger log = LoggerFactory.getLogger(RootController.class);
    
    /**
     * 이동 처리(미완성)
     * @param data
     * @return
     */
    @GetMapping("/api")
    @ResponseBody
    public Map<String,Object> getRootController(@RequestParam(name =  "win_id", required = false, defaultValue = "W000") String windowId,
            HttpServletRequest request) {
        Locale locale = request.getLocale();
        log.debug("====window open proc====");
        log.info("debug : " + windowId);
        String locationKey = "window";
        Map<String, Object> resData = new HashMap<String, Object>();
        windowId = windowId != null ? windowId : Index;
        resData.put(locationKey, windowId);
        if(isEqual(loginAuth(windowId, resData, locationKey),windowId)) {
            
        }
        //test용
       // resData.put("windows", getLangOfValueMapping((String)resData.get(locationKey), Locale.ENGLISH));
        //resData.put("headers", getLangOfValueMapping(Default, Locale.ENGLISH));
        resData.put("windows", getLangOfValueMapping((String)resData.get(locationKey), locale));
        resData.put("headers", getLangOfValueMapping(Default, locale));
        resData.put("user_name",userInfo.getUserName());
        return resData;
    }
    
    /**
     * 프로세스 처리
     * @param data (요청 데이터)
     * @return
     */
    @PostMapping("/api")
    @ResponseBody
    public Map<String,Object> postLoginWindow(@RequestBody Map<String, Object> data) {
        
        String locationKey = "window";
        //확인용
        log.info("debug : " + data.toString());
        Map<String, Object> resData = new HashMap<String, Object>();
        Object winId = data.get("win_id");
        String windowId = winId != null && !isEqual(winId, "null") ? String.valueOf(winId) : Index;
        resData.put(locationKey, windowId);
        if(isEqual(loginAuth(windowId, resData, locationKey),windowId)) {
            if(isEqual(windowId,Login)) {
                resData = userManagementService.loginProc(data, resData);
                //로그인이 완료되면 세션에 로그인 정보를 등록
                if(isEqual(resData.get(RES),SUCCESS)) {
                    //로그인 완료 후 메인화면으로
                    resData.put("window", Index);
                    userInfo.setUserName(String.valueOf(resData.get("user_name")));
                    userInfo.setLogin(true);
                }
            } else if(isEqual(windowId,SignUp)) {
                log.info("====SignUp Process Start====");
            }
            
        } else {
            //일치하지 않으면 화면 전환만 하게끔
        }  
        return resData;
    }
    
    private String loginAuth(String windowId, Map<String, Object> resData, String key) {
        //1. 유저 로그인이 되어있어야 하는 페이지인지
        if(isConfirmPath(windowId)) {
            //2. 유저가 로그인이 되어 있는지
            if(!userInfo.isLogin()) {
                //되어있지 않으면 로그인 화면으로
                resData.put(key, Login);
            } 
        } else {
            //유저가 로그인 중인지 유저가 로그인 중인데 로그인, 회원가입 화면을 띄웠는지 => 띄웠으면 기본화면으로
            if(userInfo.isLogin() && (isEqual(windowId, Login) || isEqual(windowId, SignUp))) {
                resData.put(key, Index);
            //유저가 로그인중인데 로그아웃을 호출했는지
            } else if(isEqual(windowId, Logout)){
                userInfo.setLogin(false);
                userInfo.setUserName("");
                resData.put(key, Login);
            } 
        }
        return String.valueOf(resData.get(key));
    }

}
