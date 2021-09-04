CREATE TABLE LANGUAGE_MST 
   ( WINDOW_ID VARCHAR2(20), 
    FRONT_LANG VARCHAR2(20), 
    LANG VARCHAR2(5), 
    OPTION_VALUE NUMBER(2) DEFAULT 0,
    LANG_VALUE VARCHAR2(1000), 
    REGIST_USER VARCHAR2(10), 
    REGIST_DATE DATE DEFAULT SYSDATE, 
    UPDATE_USER VARCHAR2(10), 
    UPDATE_DATE DATE DEFAULT SYSDATE, 
    UPDATE_CNT NUMBER(10,0) DEFAULT 0, 
    DEL_FLG VARCHAR2(1) DEFAULT '0',
    CONSTRAINT LANGUAGE_MST_PK PRIMARY KEY (WINDOW_ID, FRONT_LANG, LANG, OPTION_VALUE));
    
    COMMENT ON TABLE LANGUAGE_MST IS '언어 설정 마스터';
    COMMENT ON COLUMN LANGUAGE_MST.WINDOW_ID IS '화면 아이디';
    COMMENT ON COLUMN LANGUAGE_MST.FRONT_LANG IS '화면 명칭';
    COMMENT ON COLUMN LANGUAGE_MST.LANG IS '언어';
    COMMENT ON COLUMN LANGUAGE_MST.LANG_VALUE IS '언어 값';
    COMMENT ON COLUMN LANGUAGE_MST.OPTION_VALUE IS '옵션 값';
    COMMENT ON COLUMN LANGUAGE_MST.REGIST_USER IS '등록 유저';
    COMMENT ON COLUMN LANGUAGE_MST.REGIST_DATE IS '등록일';
    COMMENT ON COLUMN LANGUAGE_MST.UPDATE_USER IS '수정 유저';
    COMMENT ON COLUMN LANGUAGE_MST.UPDATE_DATE IS '수정일';
    COMMENT ON COLUMN LANGUAGE_MST.UPDATE_CNT IS '수정 횟수';
    COMMENT ON COLUMN LANGUAGE_MST.DEL_FLG IS '삭제 플래그';