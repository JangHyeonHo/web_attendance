package com.attendance.pro.controller;

import static com.attendance.pro.other.CodeMap.RES;
import static com.attendance.pro.other.CodeMap.SUCCESS;
import static com.attendance.pro.other.CodeMap.isEqual;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
     * 화면 처리
     * @param data
     * @return
     */
    @PutMapping("/api")
    @ResponseBody
    public Map<String,Object> putRootController(@RequestBody Map<String, Object> data) {
        log.info(data.toString());
        log.info(userInfo.getUserName());
        log.debug("====index window open====");
        Map<String, Object> resData = new HashMap<String, Object>();
        Object winId = data.get("win_id");
        String windowId = winId != null && !isEqual(winId, "null") ? String.valueOf(winId) : Index;
        resData.put("window", windowId);
        if(isEqual(loginAuth(windowId, resData),windowId)) {
            
        }
        resData.put("user_name",userInfo.getUserName());
        return resData;
    }
    
    /**
     * 이동 처리(미완성)
     * @param data
     * @return
     */
    @GetMapping("/api")
    @ResponseBody
    public Map<String,Object> getRootController(@RequestBody Map<String, Object> data) {
        log.info(data.toString());
        log.info(userInfo.getUserName());
        log.debug("====index window open====");
        Map<String, Object> resData = new HashMap<String, Object>();
        Object winId = data.get("win_id");
        String windowId = winId != null && !isEqual(winId, "null") ? String.valueOf(winId) : Index;
        resData.put("window", windowId);
        if(isEqual(loginAuth(windowId, resData),windowId)) {
            
        }
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
        //확인용
        log.info(data.toString());
        Map<String, Object> resData = new HashMap<String, Object>();
        Object winId = data.get("win_id");
        String windowId = winId != null && !isEqual(winId, "null") ? String.valueOf(winId) : Index;
        resData.put("window", windowId);
        if(isEqual(loginAuth(windowId, resData),windowId)) {
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
    
    private String loginAuth(String windowId, Map<String, Object> resData) {
        //1. 유저 로그인이 되어있어야 하는 페이지인지
        if(isConfirmPath(windowId)) {
            //2. 유저가 로그인이 되어 있는지
            if(!userInfo.isLogin()) {
                //되어있지 않으면 로그인 화면으로
                resData.put("window", Login);
            } 
        } else {
            //유저가 로그인 중인지 유저가 로그인 중인데 로그인, 회원가입 화면을 띄웠는지 => 띄웠으면 기본화면으로
            if(userInfo.isLogin() && (isEqual(windowId, Login) || isEqual(windowId, SignUp))) {
                resData.put("window", Index);
            //유저가 로그인중인데 로그아웃을 호출했는지
            } else if(isEqual(windowId, Logout)){
                userInfo.setLogin(false);
                userInfo.setUserName("");
                resData.put("window", Login);
            } 
        }
        return String.valueOf(resData.get("window"));
    }

}
