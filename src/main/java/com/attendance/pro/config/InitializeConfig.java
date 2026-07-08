package com.attendance.pro.config;

import static com.attendance.pro.common.CodeMap.ENGLISH;
import static com.attendance.pro.common.CodeMap.KOREAN;
import static com.attendance.pro.common.CodeMap.isEqual;

import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import com.attendance.pro.controller.WindowManagement;
import com.attendance.pro.dao.LanguageMasterDao;
import com.attendance.pro.dao.LogicServiceDao;
import com.attendance.pro.dto.LanguageMasterDto;

/**
 * 서버 기동시 초기화 처리.
 * <ul>
 *   <li>언어 마스터(LANGAUGE_MST)를 읽어 화면별 다국어 텍스트를 메모리에 적재</li>
 *   <li>로직 서비스 테이블의 일주일 지난 성공 데이터 삭제(배치 대용)</li>
 * </ul>
 */
@Component
public class InitializeConfig implements InitializingBean {

    private static List<LanguageMasterDto> langMst = null;

    private final LanguageMasterDao languageMasterDao;
    private final LogicServiceDao logicServiceDao;

    public InitializeConfig(LanguageMasterDao languageMasterDao, LogicServiceDao logicServiceDao) {
        this.languageMasterDao = languageMasterDao;
        this.logicServiceDao = logicServiceDao;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        languageSetting();
        logicServiceDeleteSetting();
    }

    /**
     * 언어 마스터를 언어별(KOR/ENG)로 WindowManagement에 적재한다.
     */
    public void languageSetting() {
        if (langMst == null) {
            langMst = languageMasterDao.getAllLangs(null, null);
        }
        if (WindowManagement.getKor() == null) {
            WindowManagement.setKor(new HashMap<>());
        }
        if (WindowManagement.getEng() == null) {
            WindowManagement.setEng(new HashMap<>());
        }
        for (LanguageMasterDto mstDto : langMst) {
            String lang = mstDto.getLang();
            String frontLang = mstDto.getFrontLang();
            if (!isEqual(mstDto.getOptionValue(), 0)) {
                frontLang = frontLang + mstDto.getOptionValue();
            }
            if (isEqual(lang, KOREAN)) {
                WindowManagement.addKor(mstDto.getWindowId(), frontLang, mstDto.getLangValue());
            } else if (isEqual(lang, ENGLISH)) {
                WindowManagement.addEng(mstDto.getWindowId(), frontLang, mstDto.getLangValue());
            } else {
                WindowManagement.addEng(mstDto.getWindowId(), frontLang, mstDto.getLangValue());
            }
        }
    }

    /**
     * 서비스 관리 테이블에 데이터가 쌓이는 것을 방지하기 위해 서버 기동시
     * 일주일 지난 성공 데이터를 삭제한다. (상시 기동 배치 서버가 없어 기동시 처리로 대체)
     */
    private void logicServiceDeleteSetting() {
        logicServiceDao.successDeleteToWeek();
    }

}
