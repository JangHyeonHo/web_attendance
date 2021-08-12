package com.attendance.pro.service;

import static com.attendance.pro.other.CodeMap.ERROR;
import static com.attendance.pro.other.CodeMap.MSG;
import static com.attendance.pro.other.CodeMap.RES;
import static com.attendance.pro.other.CodeMap.SUCCESS;

import java.util.ArrayList;
import java.util.HashMap;
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
     * @param loginData
     * @return
     */
    public Map<String, Object> loginProc(Map<String, Object> loginData, Map<String, Object> resData) {
        List<String> msgList = new ArrayList<String>();
        String userEmail = objectToString(loginData.get("user_email"));
        String userPwd = objectToString(loginData.get("user_pwd"));
        //서버에서 한 번 더 유효성을 검사한다.
        if(!loginValidationCheck(userEmail, userPwd , msgList)) {
            resData.put(MSG, msgList);
            resData.put(RES, ERROR);
            return resData;
        }
        //아이디와 비밀번호가 DB에 존재하는지 확인한다.
        UserDto user = userMasterDao.getUser(userEmail, userPwd);
        
        if(user==null || user.getUserName()==null) {
            msgList.add("존재하지 않는 이메일 혹은 비밀번호입니다.");
            resData.put(MSG, msgList);
            resData.put(RES, ERROR);
            return resData;
        }
        
        resData.put("user_name",user.getUserName());
        resData.put(RES, SUCCESS);
        return resData;
    }
    
    /**
     * 로그인 입력 체크(서버에서의 체크)
     * @param userEmail 입력한 이메일
     * @param userPwd 입력한 비밀번호
     * @param msgList 메시지 리스트를 담을 메시지 리스트
     * @return
     */
    private boolean loginValidationCheck(String userEmail, String userPwd, List<String> msgList) {
        return true;
    }

}
