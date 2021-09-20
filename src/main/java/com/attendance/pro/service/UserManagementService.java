package com.attendance.pro.service;

import static com.attendance.pro.other.CodeMap.ERROR;
import static com.attendance.pro.other.CodeMap.MSG;
import static com.attendance.pro.other.CodeMap.RES;
import static com.attendance.pro.other.CodeMap.SUCCESS;
import static com.attendance.pro.other.CodeMap.getMsg;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.attendance.pro.dao.UserMasterDao;
import com.attendance.pro.dto.UserDto;

/**
 * 유저 관리 서비스
 * @author jang
 *
 */
@Service
public class UserManagementService extends BaseService {
    
    private Logger log = LoggerFactory.getLogger(UserManagementService.class);
    
    @Autowired
    private UserMasterDao userMasterDao = null;

    /**
     * 로그인 처리 서비스
     * @param data 요청 받은 데이터
     * @param resData 반환할 데이터
     * @param windowData 화면 값(언어설정)
     * @param props 기타값
     * @return
     */
    @Override
    public Map<String, Object> proc(Map<String, Object> data, 
            Map<String, Object> resData, 
            Map<String, String> windowData,
            String... props) {
        log.debug("====Login Proc Open====");
        List<String> msgList = new ArrayList<String>();
        //요청한 이메일, 비밀번호를 받아온다.
        String userEmail = objectToString(data.get("user_email"));
        String userPwd = objectToString(data.get("user_pwd"));
        
        //서버에서 한 번 더 유효성을 검사한다.
        if(!loginValidationCheck(userEmail, userPwd , msgList, windowData)) {
            resData.put(MSG, msgList);
            resData.put(RES, ERROR);
            log.debug("====Login Proc Validation Error====");
            log.debug("Proc End ResValue : " + resData.toString());
            return resData;
        }
        
        //비밀번호를 암호화한다.
        userPwd = passwordEncrypt(userPwd);
        
        //아이디와 비밀번호가 DB에 존재하는지 확인한다.
        UserDto user = userMasterDao.getUser(userEmail, userPwd);
        
        //아이디 비밀번호가 존재하지 않는다면, 에러 메시지를 표시하고 처리를 종료한다.
        if(user==null || user.getUserName()==null) {
            msgList.add(getMsg(windowData.get("EMAILERR3"),"존재하지 않는 이메일 혹은 비밀번호입니다."));
            resData.put(MSG, msgList);
            resData.put(RES, ERROR);
            log.debug("====Login Proc Nothing ID or PWD====");
            log.debug("Proc End ResValue : " + resData.toString());
            return resData;
        }
        
        //로그인이 완료되면 해당 유저가 관리자 유저인지 확인한다.
        Integer userRank = user.getUserRank();
        resData.put("user_admin", false);
        if(userRank==-1) {
            resData.put("user_admin", true);
        }
        resData.put("user_name",user.getUserName());
        resData.put("user_cd",user.getUserCd());
        resData.put(RES, SUCCESS);
        log.debug("====Login Proc Close====");
        log.debug("Proc End ResValue : " + resData.toString());
        return resData;
    }
    
    /**
     * 로그인 입력 체크(서버에서의 체크)
     * @param userEmail 입력한 이메일
     * @param userPwd 입력한 비밀번호
     * @param msgList 메시지 리스트를 담을 메시지 리스트
     * @return
     */
    private boolean loginValidationCheck(String userEmail, String userPwd, List<String> msgList, Map<String, String> windowData) {
        String emailRegExp = "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?";
        String pwdRegExp = "^((?=.*[\\d])(?=.*[a-z])(?=.*[A-Z])|(?=.*[a-z])(?=.*[A-Z])(?=.*[^\\w\\d\\s])|(?=.*[\\d])(?=.*[A-Z])(?=.*[^\\w\\d\\s])|(?=.*[\\d])(?=.*[a-z])(?=.*[^\\w\\d\\s])).{8,30}$";
        if(userEmail==null || userEmail.trim().length()==0) {
            msgList.add(getMsg(windowData.get("EMAILERR2"),"이메일을 입력해 주세요."));
        }
        if(userPwd==null || userPwd.trim().length()==0) {
            msgList.add(getMsg(windowData.get("PWDERR1"),"비밀번호를 입력해 주세요."));
        }
        if(!userEmail.matches(emailRegExp)) {
            msgList.add(getMsg(windowData.get("EMAILERR1"),"이메일 형식이 아닙니다."));
        }
        if(!userPwd.matches(pwdRegExp)) {
            msgList.add(getMsg(windowData.get("PWDERR2"),"비밀번호는 영문자, 특수문자, 숫자를 포함하여 \n8자 이상, 30글자 미만으로 해주세요"));
        }
        if(msgList.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * 비밀번호 암호화 로직(단방향)(SHA-512)
     * @param originPwd 원래 비밀번호
     * @return 암호화된 비밀번호
     */
    private String passwordEncrypt(String originPwd) {
        String encryptPwd = new String();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(originPwd.getBytes());
            encryptPwd = String.format("%0128x", new BigInteger(1, md.digest()));
            log.info(encryptPwd);
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return encryptPwd;
    }

}
