  CREATE TABLE USER_MST 
   (USER_CD VARCHAR2(10),
    USER_EMAIL VARCHAR2(30) NOT NULL UNIQUE, 
    USER_PWD VARCHAR2(200) NOT NULL, 
    USER_NAME VARCHAR2(50), 
    USER_RANK NUMBER(2),
    USER_REG_DATE DATE DEFAULT SYSDATE,
    USER_DEL_DATE DATE,
    USER_STATUS VARCHAR2(2) DEFAULT '0',
    DEPART_CD VARCHAR2(10), 
    REGIST_USER VARCHAR2(10), 
    REGIST_DATE DATE DEFAULT SYSDATE, 
    UPDATE_USER VARCHAR2(10), 
    UPDATE_DATE DATE DEFAULT SYSDATE, 
    UPDATE_CNT NUMBER(10,0) DEFAULT 0, 
    DEL_FLG VARCHAR2(1) DEFAULT '0', 
   CONSTRAINT USER_MST_PK PRIMARY KEY (USER_CD),
   CONSTRAINT UM_DEPART_FK FOREIGN KEY(DEPART_CD) REFERENCES DEPART_MST(DEPART_CD) ON DELETE SET NULL
  );

   COMMENT ON TABLE USER_MST IS '유저 마스터';
   COMMENT ON COLUMN USER_MST.USER_CD IS '유저 코드';  
   COMMENT ON COLUMN USER_MST.USER_EMAIL IS '유저 이메일(ID)';
   COMMENT ON COLUMN USER_MST.USER_PWD IS '유저 비밀번호';
   COMMENT ON COLUMN USER_MST.USER_NAME IS '유저 이름';
   COMMENT ON COLUMN USER_MST.USER_RANK IS '유저 등급';
   COMMENT ON COLUMN USER_MST.USER_REG_DATE IS '유저 가입일'; 
   COMMENT ON COLUMN USER_MST.USER_DEL_DATE IS '유저 삭제일'; 
   COMMENT ON COLUMN USER_MST.USER_STATUS IS '유저 상태'; 
   COMMENT ON COLUMN USER_MST.DEPART_CD IS '부서 코드';
   COMMENT ON COLUMN USER_MST.REGIST_USER IS '등록 유저';
   COMMENT ON COLUMN USER_MST.REGIST_DATE IS '등록일';
   COMMENT ON COLUMN USER_MST.UPDATE_USER IS '수정 유저';
   COMMENT ON COLUMN USER_MST.UPDATE_DATE IS '수정일';
   COMMENT ON COLUMN USER_MST.UPDATE_CNT IS '수정 횟수';
   COMMENT ON COLUMN USER_MST.DEL_FLG IS '삭제 플래그';