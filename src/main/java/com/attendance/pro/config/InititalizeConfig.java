package com.attendance.pro.config;

import static com.attendance.pro.other.CodeMap.Korean;
import static com.attendance.pro.other.CodeMap.English;
import static com.attendance.pro.other.CodeMap.isEqual;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.attendance.pro.controller.WindowManagement;
import com.attendance.pro.dao.LanguageMasterDao;
import com.attendance.pro.dao.LogicServiceDao;
import com.attendance.pro.dto.LanguageMasterDto;

@Component
public class InititalizeConfig implements InitializingBean {
    
    private static List<LanguageMasterDto> langMst = null;
    
    @Autowired
    private LanguageMasterDao languageMasterDao = null;
    
    @Autowired
    private LogicServiceDao logicServiceDao = null;

    //언어 Properties 설정(Language Master에서 가져옴 언어별로 설정)
    //로직테이블 삭제 설정(배치를 따로 만들지 않았으므로...)
    @Override
    public void afterPropertiesSet() throws Exception {
        languageSetting();
        logicServiceDeleteSetting();
    }

    public void languageSetting() {
        if(langMst==null) {
            langMst = languageMasterDao.getAllLangs(null, null);
        }
        if(WindowManagement.getKor()==null) {
            WindowManagement.setKor(new HashMap<String,Map<String, String>>());
        }
        if(WindowManagement.getEng()==null) {
            WindowManagement.setEng(new HashMap<String,Map<String, String>>());
        }
        for(LanguageMasterDto mstDto : langMst) {
            String lang = mstDto.getLang();
            String frontLang = mstDto.getFrontLang();
            if(!isEqual(mstDto.getOptionValue(), 0)) {
                frontLang = frontLang+mstDto.getOptionValue();
            }
            if(isEqual(lang, Korean)) {
                WindowManagement.addKor(mstDto.getWindowId(),frontLang,mstDto.getLangValue());
            }else if(isEqual(lang, English)) {
                WindowManagement.addEng(mstDto.getWindowId(),frontLang,mstDto.getLangValue());
            } else {
                WindowManagement.addEng(mstDto.getWindowId(),frontLang,mstDto.getLangValue());
            }
            
        }
    }

    
    private void logicServiceDeleteSetting() {
        logicServiceDao.successDeleteToWeek();
    }
    
    

}
