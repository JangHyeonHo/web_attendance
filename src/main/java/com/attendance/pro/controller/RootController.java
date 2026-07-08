package com.attendance.pro.controller;

import static com.attendance.pro.common.CodeMap.RES;
import static com.attendance.pro.common.CodeMap.SUCCESS;
import static com.attendance.pro.common.CodeMap.isAnyEqual;
import static com.attendance.pro.common.CodeMap.isEqual;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.config.UserSessionInfo;
import com.attendance.pro.service.AttendanceService;
import com.attendance.pro.service.UserManagementService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 메인 루트 컨트롤러.
 * 프론트(SPA)가 /api 하나로 화면 전개(GET)와 프로세스 처리(POST)를 요청한다.
 *
 * @author jang
 */
@RestController
public class RootController extends WindowManagement {

    private static final Logger log = LoggerFactory.getLogger(RootController.class);

    private final UserSessionInfo userInfo;
    private final UserManagementService userManagementService;
    private final AttendanceService attendanceService;

    public RootController(UserSessionInfo userInfo,
            UserManagementService userManagementService,
            AttendanceService attendanceService) {
        this.userInfo = userInfo;
        this.userManagementService = userManagementService;
        this.attendanceService = attendanceService;
    }

    /**
     * 화면 전개 처리.
     * 요청받은 화면 아이디를 바탕으로 표시할 화면과 화면 데이터(언어/세션)를 돌려준다.
     */
    @GetMapping("/api")
    public Map<String, Object> getRootController(@RequestParam(name = "win_id", required = false, defaultValue = "W000") String windowId,
            @RequestParam(name = "lang", required = false) String lang,
            HttpServletRequest request) {
        log.debug("[window open window Id : {}]", windowId);
        /*
         * 1. 언어 설정 세션 확인
         * 주소값 언어가 존재하면 해당 언어를 세션에 저장하고,
         * 존재하지 않으면 세션의 언어 → 클라이언트 지역 순으로 언어를 결정한다.
         */
        Locale locale;
        if (lang != null) {
            locale = getLocales(lang);
            userInfo.setUserLocale(locale);
        } else if (userInfo.getUserLocale() == null) {
            locale = request.getLocale();
        } else {
            locale = userInfo.getUserLocale();
        }
        /*
         * 2. 화면 전개
         * 요청받은 화면 아이디를 바탕으로 어떤 화면을 띄워야 하는지 확인후 화면아이디 값을 리턴해준다.
         */
        Map<String, Object> resData = new HashMap<>();
        windowId = windowId != null ? windowId : INDEX;
        windowId = loginAuth(windowId, resData, LOCATION_KEY);
        /*
         * 3. 프로세스별 처리
         * 화면 아이디 값을 확인 후 화면 처리에 필요한 데이터를 받아온다.
         */
        Map<String, String> windowData = getLangOfValueMapping(String.valueOf(resData.get(LOCATION_KEY)), locale);
        if (isEqual(windowId, ATTENDANCE)) {
            try {
                resData = attendanceService.getAttendanceData(userInfo.getUserCd(), resData, windowData);
            } catch (Exception e) {
                log.error("attendance data load failed", e);
            }
        }
        /*
         * 4. 데이터 송신 처리
         * 1) 화면 데이터 송신 - 화면에 언어 데이터를 송신한다.
         */
        resData.put("windows", windowData != null ? windowData : "nothing");
        // 2) 헤더 데이터 송신 - 화면에 헤더 데이터를 송신한다.
        Map<String, String> headerData = getLangOfValueMapping(DEFAULT, locale);
        if (headerData != null) {
            resData.put("headers", headerData);
        }
        // 3) 세션 데이터 송신 - 화면에 세션 로그인 유저 데이터를 송신한다.
        resData.put("user_name", userInfo.getUserName());
        return resData;
    }

    /**
     * 프로세스 처리.
     *
     * @param data 요청 데이터(win_id, action 및 화면별 파라미터)
     */
    @PostMapping("/api")
    public Map<String, Object> postRootController(@RequestBody Map<String, Object> data,
            HttpServletRequest request) {
        log.debug("[proc window Id : {}]", data.get("win_id"));
        if (!isAnyEqual(data.get("win_id"), LOGIN)) {
            log.debug("data infos : {}", data);
        }
        /*
         * 1. 언어 설정 세션 확인
         * 세션에 언어가 존재하지 않으면 현 클라이언트의 지역을 취득하여 언어를 표시한다.
         */
        Locale locale = userInfo.getUserLocale() == null ? request.getLocale() : userInfo.getUserLocale();
        /*
         * 2. 화면 전개
         * 현재 세션에 로그인관련해서 문제가 없는지 확인후 문제 없으면 요청값대로 처리한다.
         */
        Map<String, Object> resData = new HashMap<>();
        //proc를 어느 화면에서 요청했는지 확인
        Object winId = data.get("win_id");
        String windowId = winId != null && !isEqual(winId, "null") ? String.valueOf(winId) : INDEX;
        String authId = loginAuth(windowId, resData, LOCATION_KEY);
        Map<String, String> windowData = getLangOfValueMapping(String.valueOf(resData.get(LOCATION_KEY)), locale);
        /*
         * 3. 프로세스별 처리
         * 로그인 인증 결과와 요청 화면이 일치할 때만 화면 로직을 실행한다.
         * 일치하지 않으면 화면 전환값만 돌려준다.
         */
        if (isEqual(authId, windowId)) {
            if (isEqual(windowId, LOGIN)) {
                resData = userManagementService.proc(data, resData, windowData);
                //로그인이 완료되면 세션에 로그인 정보를 등록
                if (isEqual(resData.get(RES), SUCCESS)) {
                    userInfo.setUserName(String.valueOf(resData.get("user_name")));
                    userInfo.setUserCd(String.valueOf(resData.get("user_cd")));
                    userInfo.setLogin(true);
                    //해당 유저가 관리자 유저라면 관리자 화면을, 아니면 출결 화면을 호출
                    if ((boolean) resData.get("user_admin")) {
                        resData.put(LOCATION_KEY, ADMIN);
                        userInfo.setAdmin(true);
                    } else {
                        resData.put(LOCATION_KEY, ATTENDANCE);
                    }
                }
            } else if (isEqual(windowId, SIGN_UP)) {
                log.info("====SignUp Process Start====");
            } else if (isAnyEqual(windowId, ATTENDANCE, ATT_DETAILS)) {
                resData = attendanceService.proc(data, resData, windowData, userInfo.getUserCd());
            }
        }
        return resData;
    }

    /**
     * 로그인 인증에 따른 표시 화면 결정.
     *
     * @param windowId 요청한 화면 값
     * @param resData  반환할 데이터
     * @param key      화면 키(LOCATION_KEY 고정)
     * @return 실제로 표시할 화면 아이디
     */
    private String loginAuth(String windowId, Map<String, Object> resData, String key) {
        //0. 화면데이터를 먼저 입력
        resData.put(key, windowId);
        //1. 유저 로그인이 되어있어야 하는 페이지인지
        if (isConfirmPath(windowId)) {
            //2. 유저가 로그인이 되어 있는지 - 되어있지 않으면 로그인 화면으로
            if (!userInfo.isLogin()) {
                resData.put(key, LOGIN);
            }
        } else {
            //유저가 로그인 중인데 로그인, 회원가입, 홈 화면을 띄웠으면 출결화면으로
            if (userInfo.isLogin() && isAnyEqual(windowId, LOGIN, SIGN_UP, INDEX)) {
                resData.put(key, ATTENDANCE);
            } else if (isEqual(windowId, LOGOUT)) {
                //유저가 로그인중인데 로그아웃을 호출했는지
                userInfo.sessionClear();
                resData.put(key, LOGIN);
            }
        }
        return String.valueOf(resData.get(key));
    }

}
