package com.attendance.pro.service;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import static com.attendance.pro.other.CodeMap.isEqual;
import static com.attendance.pro.other.CodeMap.RESULT;

import org.springframework.beans.factory.annotation.Autowired;

import com.attendance.pro.dao.LogicServiceDao;

public class BaseService implements BaseServiceImpl{
    
    @Autowired
    LogicServiceDao logicServiceDao = null;
    
    protected String objectToString(Object e) {
        if(e==null) {
            return null;
        }
        if(e instanceof String) {
            return (String) e;
        }
        return e.toString();
    }
    
    protected Integer objectToInteger(Object e) {
        try {
            String s = objectToString(e);
            if(e instanceof Integer) {
                return (Integer) e;
            }
            return Integer.valueOf(s);
        } catch(NumberFormatException ex) {
            return null;
        }
    }
    
    protected Boolean objectToBoolean(Object e) {
        String s = objectToString(e);
        if(e instanceof Boolean) {
            return Boolean.valueOf(s);
        }
        return null;
    }
    
    protected Double objectToDouble(Object e) {
        try {
            String s = objectToString(e);
            if(e instanceof Double) {
                return (Double) e;
            }
            return Double.valueOf(s);
        } catch(NumberFormatException ex) {
            return null;
        }
    }
    
    /**
     * 데이터 체크후 확정전 변조 확인용
     * @param dto
     * @param checkService
     * @return result
     */
    protected String checkRegistSystem(Map<String, Object> data, String checkService, String userCd) throws Exception {
        String result = getResult(userCd);
        data.put(RESULT, result);
        try {
            String retData = retDataEncrypt(data);
            Integer i = logicServiceDao.insertService(result, retData, checkService, userCd, "S", null, null);
        } catch (NoSuchAlgorithmException e) {
            errorRegistSystem(data, checkService, userCd, "CRSERR", e.getMessage());
        }
        return result;
    }
    
    /**
     * 체크로직 데이터 암호화
     * @param data
     * @return
     * @throws NoSuchAlgorithmException
     */
    private String retDataEncrypt(Map<String, Object> data) throws NoSuchAlgorithmException {
        String retData = null;
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(data.toString().getBytes());
        retData = String.format("%0128x", new BigInteger(1, md.digest()));
        return retData;
    }
    
    /**
     * 체크전 데이터와 비교
     * @param result
     * @param data
     * @param userCd
     * @return
     */
    protected boolean isResultDatasEqual(String result, Map<String, Object> data, String userCd) {
        try {
            String retData = retDataEncrypt(data);
            String encryptData = logicServiceDao.getCheckLogic(result, userCd);
            return isEqual(retData,encryptData);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
    
    /**
     * 변조코드 출력
     * @param userCd
     * @return
     */
    private String getResult(String userCd) {
        String result = null;
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSS");
        result = sdf.format(date)+userCd;
        return result;
    }
    
    /**
     * 에러 처리 로직
     * @param dto
     * @param checkService
     * @return result
     */
    protected void errorRegistSystem(Map<String, Object>  data, String checkService, String userCd, String errorCd, String errorMsg) {
        String result = getResult(userCd);
        data.put(RESULT, result);
        if(errorMsg.length()>400) {
            errorMsg = errorMsg.substring(0, 400);
        }
        Integer i = logicServiceDao.insertService(result ,data.toString(), checkService, userCd, "E" , errorCd, errorMsg);
    }

    @Override
    public Map<String, Object> proc(Map<String, Object> data, 
            Map<String, Object> resData, Map<String, String> windowData,
            String... props) {
        
        return resData;
    }

}
