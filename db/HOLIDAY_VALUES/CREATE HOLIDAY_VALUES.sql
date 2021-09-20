CREATE TABLE HOLIDAY_VALUES 
   (HOLIDAY_SEQ NUMBER(11),
    HOLIDAY_NAME VARCHAR2(100), 
    HOLIDAY_COMMENT VARCHAR2(300),
    REGIST_USER VARCHAR2(10) DEFAULT 'SYSTEM', 
    REGIST_DATE DATE DEFAULT SYSDATE, 
    UPDATE_USER VARCHAR2(10) DEFAULT 'SYSTEM', 
    UPDATE_DATE DATE DEFAULT SYSDATE, 
    UPDATE_CNT NUMBER(10,0) DEFAULT 0, 
    DEL_FLG VARCHAR2(1) DEFAULT '0', 
   CONSTRAINT HOLIDAY_VALUES_PK PRIMARY KEY (HOLIDAY_SEQ)
  );

   COMMENT ON TABLE HOLIDAY_VALUES IS '휴가 종류';
   COMMENT ON COLUMN HOLIDAY_VALUES.HOLIDAY_SEQ IS '휴가 번호';  
   COMMENT ON COLUMN HOLIDAY_VALUES.HOLIDAY_NAME IS '휴가 명';
   COMMENT ON COLUMN HOLIDAY_VALUES.HOLIDAY_COMMENT IS '휴가 설명';
   COMMENT ON COLUMN HOLIDAY_VALUES.REGIST_USER IS '등록 유저';
   COMMENT ON COLUMN HOLIDAY_VALUES.REGIST_DATE IS '등록일';
   COMMENT ON COLUMN HOLIDAY_VALUES.UPDATE_USER IS '수정 유저';
   COMMENT ON COLUMN HOLIDAY_VALUES.UPDATE_DATE IS '수정일';
   COMMENT ON COLUMN HOLIDAY_VALUES.UPDATE_CNT IS '수정 횟수';
   COMMENT ON COLUMN HOLIDAY_VALUES.DEL_FLG IS '삭제 플래그';