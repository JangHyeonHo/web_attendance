package com.attendance.pro.service;

import static com.attendance.pro.common.CodeMap.RESULT;
import static com.attendance.pro.common.CodeMap.isEqual;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;

import com.attendance.pro.dao.LogicServiceDao;

/**
 * 프로세스 서비스 공통 기능(형변환, 변조 체크, 에러 기록).
 */
public abstract class BaseService implements ProcService {

    private final LogicServiceDao logicServiceDao;

    protected BaseService(LogicServiceDao logicServiceDao) {
        this.logicServiceDao = logicServiceDao;
    }

    protected String objectToString(Object e) {
        if (e == null) {
            return null;
        }
        if (e instanceof String s) {
            return s;
        }
        return e.toString();
    }

    protected Integer objectToInteger(Object e) {
        if (e instanceof Integer i) {
            return i;
        }
        try {
            String s = objectToString(e);
            return s == null ? null : Integer.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    protected Boolean objectToBoolean(Object e) {
        if (e instanceof Boolean b) {
            return b;
        }
        return null;
    }

    protected Double objectToDouble(Object e) {
        if (e instanceof Double d) {
            return d;
        }
        try {
            String s = objectToString(e);
            return s == null ? null : Double.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 데이터 체크 후 확정 전 변조 확인용.
     * 체크 시점의 요청 데이터를 해시로 기록해 두고, 확정 시점에 동일한지 비교한다.
     *
     * @param data         요청 데이터
     * @param checkService 체크를 요청한 서비스 명
     * @param userCd       유저 코드
     * @return 변조 확인 키(result)
     */
    protected String checkRegistSystem(Map<String, Object> data, String checkService, String userCd) throws Exception {
        String result = getResult(userCd);
        data.put(RESULT, result);
        try {
            String retData = retDataEncrypt(data);
            logicServiceDao.insertService(result, retData, checkService, userCd, "S", null, null);
        } catch (NoSuchAlgorithmException e) {
            errorRegistSystem(data, checkService, userCd, "CRSERR", e.getMessage());
        }
        return result;
    }

    /**
     * 체크로직 데이터 해시화(SHA-512).
     */
    private String retDataEncrypt(Map<String, Object> data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(data.toString().getBytes());
        return HexFormat.of().formatHex(md.digest());
    }

    /**
     * 확정 시점의 데이터가 체크 시점의 데이터와 일치하는지 비교한다.
     */
    protected boolean isResultDatasEqual(String result, Map<String, Object> data, String userCd) {
        try {
            String retData = retDataEncrypt(data);
            String encryptData = logicServiceDao.getCheckLogic(result, userCd);
            return isEqual(retData, encryptData);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    /**
     * 변조 확인 키 생성(타임스탬프 + 유저 코드).
     */
    private String getResult(String userCd) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSS");
        return sdf.format(new Date()) + userCd;
    }

    /**
     * 에러 내용을 로직 서비스 테이블에 기록한다.
     */
    protected void errorRegistSystem(Map<String, Object> data, String checkService, String userCd, String errorCd, String errorMsg) {
        String result = getResult(userCd);
        data.put(RESULT, result);
        if (errorMsg.length() > 400) {
            errorMsg = errorMsg.substring(0, 400);
        }
        logicServiceDao.insertService(result, data.toString(), checkService, userCd, "E", errorCd, errorMsg);
    }

}
