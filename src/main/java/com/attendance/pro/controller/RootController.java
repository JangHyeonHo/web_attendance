package com.attendance.pro.controller;

import static com.attendance.pro.other.CodeMap.RES;
import static com.attendance.pro.other.CodeMap.SUCCESS;
import static com.attendance.pro.other.CodeMap.isEqual;
import static com.attendance.pro.other.CodeMap.isEqualMultyisOne;

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
import com.attendance.pro.service.AttendanceService;
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
    
    @Autowired
    private AttendanceService attendanceService = null;
    
    private Logger log = LoggerFactory.getLogger(RootController.class);
    
    /**
     * 화면 처리(미완성)
     * @param data
     * @return
     */
    @GetMapping("/api")
    @ResponseBody
    public Map<String,Object> getRootController(@RequestParam(name =  "win_id", required = false, defaultValue = "W000") String windowId,
            @RequestParam(name =  "lang", required = false) String lang,
            HttpServletRequest request) {
        log.debug("[window open "  + "window Id :" + windowId + "]");
        //1. 언어 설정 세션 확인
        /* 주소값 언어가 존재하지 않으면 현 클라이언트의 지역을 취득하여 언어를 표시
            단 세션에 언어가 설정이 되어 있으면 해당 언어로 표시를 한다.
            세션에 언어가 표시되어 있지 않으면 지역을 취득하여 언어를 표시한다.
         */
        Locale locale = null;
        if(lang!=null) {
            locale = getLocales(lang);
            userInfo.setUserLocale(locale);
        } else {
            if(userInfo.getUserLocale()==null) {
                locale = request.getLocale();
            } else {
                locale = userInfo.getUserLocale();
            }
        }
        //1. 끝
        //2. 화면 전개
        /*
         * 요청받은 화면 아이디를 바탕으로 어떤 화면을 띄워야 하는지 확인후 화면아이디 값을 리턴해준다.
         */
        String locationKey = LocationKey;
        Map<String, Object> resData = new HashMap<String, Object>();
        windowId = windowId != null ? windowId : Index;
        windowId = loginAuth(windowId, resData, locationKey);
        //2. 끝
        //3. 프로세스별 처리
        /*
         * 화면 아이디 값을 확인 후 화면 처리에 필요한 데이터를 받아온다.
         */
        Map<String, String> windowData = getLangOfValueMapping(String.valueOf(resData.get(locationKey)), locale);
        if(isEqual(windowId,Login)) {
            //log.info("====Login Process Start====");
        } else if(isEqual(windowId,SignUp)) {
            //log.info("====SignUp Process Start====");
        } else if(isEqual(windowId,Attendance)) {
            try {
                resData = attendanceService.getAttendanceData(userInfo.getUserCd(), resData, windowData);
            } catch (Exception e) {
                log.error(e.getMessage());
                e.printStackTrace();
            }
        }
        //3. 끝
        //4. 데이터 송신 처리
        /*
         * 1) 화면 데이터 송신
         * 화면에 언어 데이터를 송신한다.
         */
        if(windowData!=null) {
            resData.put("windows", windowData);
        } else {
            resData.put("windows", "nothing");
        }
        /*
         * 2) 헤더 데이터 송신
         * 화면에 헤더 데이터를 송신한다.
         */
        Map<String, String> headerData = getLangOfValueMapping(Default, locale);
        if(headerData!=null) {
            resData.put("headers", headerData);
        }
        /*
         * 3) 세션 데이터 송신
         * 화면에 세션 로그인 유저 데이터를 송신한다.
         */
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
    public Map<String,Object> postRootController(@RequestBody Map<String, Object> data,
            HttpServletRequest request) {
        log.debug("[proc "  + "window Id :" + data.get("win_id") + "]");
        if(!isEqualMultyisOne(data.get("win_id"), Login)) {
            log.debug("data infos : " + data.toString());
        }
        //1. 언어 설정 세션 확인
        /* 세션에 언어가 존재하지 않으면 현 클라이언트의 지역을 취득하여 언어를 표시
            세션에 언어가 표시되어 있지 않으면 지역을 취득하여 언어를 표시한다.
         */
        Locale locale = null;
        if(userInfo.getUserLocale()==null) {
            locale = request.getLocale();
        } else {
            locale = userInfo.getUserLocale();
        }
        //1. 끝
        //2. 화면 전개
        /*
         * 현재 세션에 로그인관련해서 문제가 없는지 확인후 문제 없으면 요청값대로 처리한다.
         */
        String locationKey = LocationKey;
        //반환값 설정
        Map<String, Object> resData = new HashMap<String, Object>();
        //proc를 어느 화면에서 요청했는지 확인
        Object winId = data.get("win_id");
        String windowId = winId != null && !isEqual(winId, "null") ? String.valueOf(winId) : Index;
        String authId = loginAuth(windowId, resData, locationKey);
        Map<String, String> windowData = getLangOfValueMapping(String.valueOf(resData.get(locationKey)), locale);
        //2. 끝
        //3. 프로세스별 처리
        /*
         * 화면에 맞게 처리한다.
         */
        if(isEqual(authId,windowId)) {
            //요청한 화면 로직에 맞게 처리를 해야한다면.
            if(isEqual(windowId,Login)) {
                resData = userManagementService.proc(data, resData, windowData);
                //로그인이 완료되면 세션에 로그인 정보를 등록
                if(isEqual(resData.get(RES),SUCCESS)) {
                    //로그인 완료 후 출결화면으로
                    userInfo.setUserName(String.valueOf(resData.get("user_name")));
                    userInfo.setUserCd(String.valueOf(resData.get("user_cd")));
                    userInfo.setLogin(true);
                    //해당 유저가 관리자 유저라면 관리자 화면을 호출
                    if((boolean) (resData.get("user_admin"))) {
                        resData.put(locationKey, Admin);
                        userInfo.setAdmin(true);
                    } else {
                        resData.put(locationKey, Attendance);
                    }
                }
            } else if(isEqual(windowId,SignUp)) {
                log.info("====SignUp Process Start====");
            } else if(isEqualMultyisOne(windowId,Attendance, AttDetails)) {
                resData = attendanceService.proc(data, resData, windowData, userInfo.getUserCd());
            } 
        } else {
            //일치하지 않으면 화면 전환만 하게끔
        }  
        return resData;
    }
    
    /**
     * 로그인 화면 설정
     * @param windowId 요청한 화면 값
     * @param resData 반환할 데이터
     * @param key 화면 키(LocationKey 고정)
     * @return
     */
    private String loginAuth(String windowId, Map<String, Object> resData, String key) {
        //0. 화면데이터를 먼저 입력
        resData.put(key, windowId);
        //1. 유저 로그인이 되어있어야 하는 페이지인지
        if(isConfirmPath(windowId)) {
            //2. 유저가 로그인이 되어 있는지
            if(!userInfo.isLogin()) {
                //되어있지 않으면 로그인 화면으로
                resData.put(key, Login);
            } 
        } else {
            //유저가 로그인 중인지 유저가 로그인 중인데 로그인, 회원가입, 홈 화면을 띄웠는지 => 띄웠으면 출결화면으로
            if(userInfo.isLogin() && (isEqual(windowId, Login) || isEqual(windowId, SignUp) || isEqual(windowId, Index))) {
                resData.put(key, Attendance);
            //유저가 로그인중인데 로그아웃을 호출했는지
            } else if(isEqual(windowId, Logout)){
                userInfo.sessionClear();
                resData.put(key, Login);
            } 
        }
        return String.valueOf(resData.get(key));
    }

}
