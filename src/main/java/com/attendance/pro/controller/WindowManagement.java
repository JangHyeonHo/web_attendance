package com.attendance.pro.controller;

import static com.attendance.pro.common.CodeMap.isAnyEqual;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.attendance.pro.common.CodeMap;

/**
 * 화면 관리.
 * 화면 아이디(W000~) 정의와 화면별 다국어 텍스트(언어 마스터)를 보관한다.
 * 프론트에 실제 화면 명 대신 화면 아이디를 노출하기 위한 용도.
 *
 * @author jang
 */
public class WindowManagement {

    protected static final String LOCATION_KEY = "window";

    private static Map<String, Map<String, String>> kor = null;
    private static Map<String, Map<String, String>> eng = null;

    //화면 아이디
    public static final String DEFAULT = "W999";
    public static final String INDEX = "W000";
    public static final String LOGIN = "W001";
    public static final String LOGOUT = "W002";
    public static final String SIGN_UP = "W003";
    public static final String ADMIN = "W004";
    public static final String ATTENDANCE = "W005";
    public static final String ATT_DETAILS = "W006";

    private final Map<String, Boolean> confirmPath = createConfirmPath();

    public boolean isConfirmPath(String windowId) {
        Boolean confirm = confirmPath.get(windowId);
        return confirm != null ? confirm : confirmPath.get(INDEX);
    }

    /**
     * 로그인이 필요한 화면은 true, 필요없는 화면은 false.
     */
    private static Map<String, Boolean> createConfirmPath() {
        Map<String, Boolean> path = new HashMap<>();
        path.put(INDEX, false);
        path.put(LOGIN, false);
        path.put(LOGOUT, false);
        path.put(SIGN_UP, false);
        path.put(ADMIN, false); //TODO test용 false -> 관리자 화면 완성 후 true로 변경
        path.put(ATTENDANCE, true);
        path.put(ATT_DETAILS, true);
        path.put(DEFAULT, false);
        return path;
    }

    /**
     * 화면 아이디와 로케일에 해당하는 다국어 텍스트 맵을 취득한다.
     */
    public static Map<String, String> getLangOfValueMapping(String windowId, Locale locale) {
        if (isAnyEqual(locale, Locale.KOREAN, Locale.KOREA)) {
            return kor.get(windowId) != null ? kor.get(windowId) : new HashMap<>();
        } else if (isAnyEqual(locale, Locale.JAPANESE, Locale.JAPAN)) {
            //일본어 마스터가 아직 없으므로 한국어로 대체
            return kor.get(windowId) != null ? kor.get(windowId) : new HashMap<>();
        } else {
            return eng.get(windowId) != null ? eng.get(windowId) : new HashMap<>();
        }
    }

    public static Map<String, Map<String, String>> getKor() {
        return kor;
    }

    public static void setKor(Map<String, Map<String, String>> kor) {
        WindowManagement.kor = kor;
    }

    public static Map<String, Map<String, String>> getEng() {
        return eng;
    }

    public static void setEng(Map<String, Map<String, String>> eng) {
        WindowManagement.eng = eng;
    }

    public static void addKor(String winId, String key, String value) {
        kor.computeIfAbsent(winId, k -> new HashMap<>()).put(key, value);
    }

    public static void addEng(String winId, String key, String value) {
        eng.computeIfAbsent(winId, k -> new HashMap<>()).put(key, value);
    }

    public static List<String> getAllWindowId() {
        return List.of(INDEX, LOGIN, LOGOUT, SIGN_UP, ADMIN, ATTENDANCE, ATT_DETAILS, DEFAULT);
    }

    public static List<String> getAllLangs() {
        return List.of(CodeMap.KOREAN, CodeMap.ENGLISH);
    }

    public Locale getLocales(String lang) {
        if (isAnyEqual(lang, CodeMap.KOREAN, "KO")) {
            return Locale.KOREAN;
        } else if (isAnyEqual(lang, CodeMap.ENGLISH, "EN")) {
            return Locale.ENGLISH;
        }
        return Locale.KOREAN;
    }

}
