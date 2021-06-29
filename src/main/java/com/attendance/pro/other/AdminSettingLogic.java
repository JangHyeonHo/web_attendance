package com.attendance.pro.other;

import static com.attendance.pro.other.CodeMap.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import com.attendance.pro.dao.AdminScanDao;
import com.attendance.pro.dao.DaoManagement;

@Component
public class AdminSettingLogic {
    
    /**
     * 로그작성
     * */
    private Logger log = LoggerFactory.getLogger(AdminSettingLogic.class);
    
    //기호
    public static final String COMMA = ",";
    public static final String OPEN_PAR = "(";
    public static final String CLOSE_PAR = ")";
    public static final String OPEN_BRA = "{";
    public static final String CLOSE_BRA = "}";
    public static final String COLONE = ":";
    public static final String SIN_QUO = "'";
    public static final String DOB_QUO = "\"";
    public static final String BLANK = "";
    public static final String SPACE = " ";
    public static final String DAT = ".";
    public static final String CRLF = "\r\n";
    public static final String LF =  "\n";
    public static final String TAB =  "\t";
    public static final String SLASH =  "/";
    public static final String UNDER_BAR =  "_";
    
    //속성값
    public static final String NOT_NULL = "not null";
    public static final String MODIFY = "MODIFY";
    public static final String ADD = "ADD";
    public static final String YES = "Y";
    public static final String NO = "N";
    public static final String PK = "pk";
    public static final String DEFAULT = "default";
    public static final String COMMENT = "{table}.{column} IS '{comment}'";
    public static final String PK_LIST = "CONSTRAINT {pkName} PRIMARY KEY( {pkList} )";
    public static final String TSV_FILE_EXTENSION = ".tsv";
    
    //public static final String FK = "Foreign Key";
    

    @Autowired
    AdminScanDao adminScanDao;
    
    //제외 컬럼 확인용
    private boolean exceptColumn(String columns) {
        if(isEqualMultyisOne(columns,"REGIST_USER"
                , "REGIST_DATE"
                , "UPDATE_USER"
                , "UPDATE_DATE"
                , "UPDATE_CNT"
                , "DEL_FLG")) {
            return true;
        }
        return false;
    }
    
    //DB테이블 등록 시스템
    public boolean proc(ModelAndView model) {
        List<String> successModel = new ArrayList<String>();
        try {
            log.info("TABLE MANAGEMENT SYSTEM");
            //자바에 등록된 테이블 정보 취득
            Map<String, String> map = DaoManagement.getColumns();
            //Management에 등록되어있는 테이블이 있는지 참조
            for(String table : DaoManagement.Daos) {
                //Management의 테이블 칼럼정보를 호출한다.
                String data = map.get(table);
                String[] columns = data.replace(OPEN_BRA,BLANK)
                                                        .replace(CLOSE_BRA, BLANK)
                                                        .split(COMMA);
                //신규 컬럼이 있는지 체크하는 맵
                Map<String, Boolean> newColumnsCheck = new HashMap<String, Boolean>();
                if(isStringEqual(adminScanDao.isTableExisting(table))){
                    //테이블이 DB에 존재한다면
                    //해당 테이블의 내용이 수정이 되어있는지 컬럼확인
                    List<Map<String, String>> columnInfos = adminScanDao.getTableInformations(table);
                    log.info(table + " is Exist");
                    for(Map<String, String> info : columnInfos) {
                        //제외대상(테이블 고정값)
                        if(exceptColumn(info.get("COLUMN_NAME"))){
                            continue;
                        }
                        boolean isNameExist = false;
                        for(String column : columns) {
                            //변화하는 값이 있는지 확인
                            String alterComment = BLANK;
                            String alterNotNull = BLANK;
                            String alterDefault = BLANK;
                            String alterDBType = BLANK;
                            //칼럼을 칼럼명과 속성으로 구분
                            String columnName = column.split(COLONE)[0].trim();
                            String columnAttribute = column.split(COLONE)[1].trim();
                            //제외대상(테이블 고정값)
                            if(exceptColumn(columnName)) {
                                continue;
                            }
                            //테이블의 칼럼명이 존재하는지 확인
                            if(isEqual(columnName , info.get("COLUMN_NAME"))) {
                                newColumnsCheck.put(columnName, true);
                                isNameExist = true;
                                //존재한다면 데이터값이 일치한지 확인
                                //1. 속성 코멘트 취득
                                String isEqualComment = BLANK;
                                if(columnAttribute.indexOf(DOB_QUO)!=-1) {
                                    //매니지먼트에 코멘트가 존재하는 경우
                                    isEqualComment = columnAttribute.substring(columnAttribute.indexOf(DOB_QUO)+1
                                            , columnAttribute.lastIndexOf(DOB_QUO));
                                    //일치하지 않는다면 변환해야므로 취득
                                    if(!isEqual(isEqualComment , info.get("COMMENTS"))){
                                        alterComment = COMMENT.replace("{table}", table)
                                                                                        .replace("{column}", columnName)
                                                                                        .replace("{comment}", isEqualComment);
                                    }
                                } else {
                                    //존재하지 않는 경우
                                    if(!isEmpty(info.get("COMMENTS"))){
                                        //DB에는 존재하는 경우 삭제처리를 위해 '' 데이터 입력
                                        alterComment = COMMENT.replace("{table}", table)
                                                .replace("{column}", columnName)
                                                .replace("{comment}", BLANK);
                                    }
                                }
                                
                                //2. 속성 Not null 취득(PK도 NOT NULL취급) 
                                if(columnAttribute.contains(NOT_NULL) || columnAttribute.contains(PK)) {
                                    //매니지먼트에 NOT NULL이 존재하는 경우
                                    if(!isEqual(NO , info.get("NULLABLE"))){
                                        //DB에는 NO일 경우 NOT_NULL설정
                                        alterNotNull = "NOT_NULL";
                                    }
                                } else {
                                    //매니지먼트에 NOT NULL이 존재하지 않는 경우
                                    if(!isEqual(YES , info.get("NULLABLE"))){
                                        //DB에는 NO일 경우 NULL설정
                                        alterNotNull = "NULL";
                                    }
                                }
                                
                                //3.DEFAULT값 취득
                                String columnDefault = BLANK;
                                if(columnAttribute.contains(DEFAULT)) {
                                    columnDefault = columnAttribute.substring(columnAttribute.indexOf(DEFAULT)+DEFAULT.length() +1
                                            , columnAttribute.indexOf(CLOSE_PAR, columnAttribute.indexOf(DEFAULT)));
                                    //매니지먼트에 DEFAULT가 존재하는 경우
                                    if(!isEqual(columnDefault , info.get("DATA_DEFAULT"))){
                                        //DB의 값과 DEFAULT 값이 일치하지 않는 경우
                                        alterDefault = "DEFAULT " + columnDefault;
                                    }
                                    
                                } else {
                                    //매니지먼트에 DEFAULT가 존재하지 않는 경우
                                    if(!isEqual(null , info.get("DATA_DEFAULT"))){
                                        //DB에는 NO일 경우 NOT_NULL설정
                                        alterDefault = "DEFAULT NULL";
                                    }
                                }

                                //5. 속성 DB타입취득 (속성은 필수이므로 없는것은 존재하지 않음)
                                int spaceIndex = columnAttribute.indexOf(SPACE);
                                boolean isTypeEqual = true;
                                String dbType = columnAttribute;
                                String dbDataType = BLANK;
                                String dbDataLength = BLANK;
                                if(spaceIndex!=-1) {
                                    dbType = columnAttribute.substring(0, spaceIndex);
                                }
                                //매니지먼트 값이 데이터 길이가 존재하는지 확인
                                int openParIndex = dbType.indexOf(OPEN_PAR);
                                if(openParIndex!=-1) {
                                    dbDataType = dbType.substring(0, openParIndex);
                                    dbDataLength = dbType.substring(openParIndex+1, dbType.indexOf(CLOSE_PAR));
                                } else {
                                    dbDataType = dbType;
                                }
                                //CHAR, VARCHAR2, NCHAR, NVARCHAR2, NUMBER의 경우에만 데이터 값 비교
                                switch(dbDataType) {
                                case "NUMBER":
                                case "CHAR":
                                case "VARCHAR2":
                                case "NCHAR":
                                case "NVARCHAR2":
                                    //데이터 길이 비교
                                    if(!isEqual(info.get("DATA_LENGTH"), dbDataLength)) {
                                        isTypeEqual = false;
                                    }
                                default :
                                    //그 외에는 데이터 타입 비교
                                    if(!isEqual(info.get("DATA_TYPE"), dbDataType)) {
                                        isTypeEqual = false;
                                    }
                                    break;
                                }
                                //속성값이 일치하지 않음
                                if(!isTypeEqual) {
                                    alterDBType = dbType;
                                }
                                
                            } else {
                                //존재하지 않는다면 존재하는 데이터가 있는지 확인
                                continue;
                            }
                           
                            //처리 완료후(수정이 되어 있는 경우) => ALTER TABLE개시
                            if(!isEqual(alterComment, BLANK)) {
                                log.info("CHANGE "+table + DAT + columnName + " COMMENT " + alterComment);
                                successModel.add("COMMANT ON " + table + DAT  + columnName +" END");
                                adminScanDao.commentOn(alterComment);
                            }
                            if(!isEqual(alterNotNull, BLANK)) {
                                log.info("CHANGE "+table + DAT + columnName + " COMMENT " + alterComment);
                                successModel.add("ALTER TABLE " + table + DAT  + columnName + SPACE + alterNotNull +" END");
                                adminScanDao.alterTable(alterNotNull);
                            }
                            if(!isEqual(alterDefault, BLANK)) {
                                successModel.add("ALTER TABLE " + table + DAT  + columnName + SPACE + alterDefault +" END");
                            }
                            if(!isEqual(alterDBType, BLANK)) {
                                successModel.add("ALTER TABLE " + table + DAT  + columnName + SPACE + alterDBType +" END");
                            }
                            
                        }
                        
                        //매니지먼트에서 삭제가 된 DB테이블 컬럼
                        if(!isNameExist) {
                            successModel.add("ALTER TABLE " + table + DAT  + "MODIFY" + SPACE + info.get("COLUMN_NAME") +" END");
                        }
                        
                    }
                    
                    //신규 칼럼이 존재할 경우
                    if(columnInfos.size()<columns.length) {
                        for(String column : columns) {
                            String columnName = column.split(COLONE)[0].trim();
                            if(exceptColumn(columnName)) {
                                continue;
                            }
                            if(isEmpty(newColumnsCheck.get(columnName))) {
                                //신규 컬럼 추가 로직
                                successModel.add("ALTER TABLE " + table + DAT  + "ADD" + SPACE + "" +" END");
                            };
                        }
                    }
                    

                    //테이블이 수정이 되었다면
                    successModel.add("ALTER TABLE " + table + " END");
                    
                } else {
                    successModel.add("CREATE TABLE " + table + " END");
                    //테이블이 존재하지 않으면
                    StringBuffer inSetData = new StringBuffer();
                    List<String> commentList = new ArrayList<String>();
                    StringBuffer pkList = new StringBuffer();
                    String registPk = "";
                    inSetData.append(OPEN_PAR);
                    boolean isFirst = true;
                    //디비형식 제작
                    for(String column : columns) {
                        //첫 칼럼이 아니라면 콤마 삽입
                        if(!isFirst) {
                            inSetData.append(COMMA);
                        }
                        //칼럼을 칼럼명과 속성으로 구분
                        String columnName = column.split(COLONE)[0].trim();
                        String columnAttribute = column.split(COLONE)[1].trim();
                        //1. 속성 코멘트 취득
                        String columnComment = BLANK;
                        if(columnAttribute.indexOf(DOB_QUO)!=-1) {
                            columnComment = columnAttribute.substring(columnAttribute.indexOf(DOB_QUO)
                                    , columnAttribute.lastIndexOf(DOB_QUO)+1);
                            columnComment.replaceAll(DOB_QUO, SIN_QUO);
                        }
                        //2. 속성 Not null 취득
                        boolean isNotNull = false;
                        if(columnAttribute.contains(NOT_NULL)) {
                            isNotNull = true;
                        }
                        //3. 속성 PK 취득
                        if(columnAttribute.contains(PK)) {
                            pkList.append(columnName);
                            pkList.append(COMMA);
                        }
                        //4.DEFAULT값 취득
                        boolean isDefault = false;
                        String columnDefault = BLANK;
                        if(columnAttribute.contains(DEFAULT)) {
                            columnDefault = columnAttribute.substring(columnAttribute.indexOf(DEFAULT)+DEFAULT.length() +1
                                            , columnAttribute.indexOf(CLOSE_PAR, columnAttribute.indexOf(DEFAULT)));
                            isDefault = true;
                        }
                        
                        //5. 속성 DB타입취득
                        int spaceIndex = columnAttribute.indexOf(SPACE);
                        String dbType = columnAttribute;
                        if(spaceIndex!=-1) {
                            dbType = columnAttribute.substring(0, spaceIndex);
                        }
                        //6. DB정보에 입력
                        inSetData.append(columnName);
                        inSetData.append(SPACE);
                        inSetData.append(dbType);
                        inSetData.append(SPACE);
                        if(isDefault) {
                            inSetData.append("DEFAULT");
                            inSetData.append(SPACE);
                            inSetData.append(columnDefault);
                            inSetData.append(SPACE);
                        }
                        if(isNotNull) {
                            inSetData.append("NOT NULL");
                            inSetData.append(SPACE);
                        }
                        if(!columnComment.equals(BLANK)) {
                            String comment = COMMENT.replace("{table}", table)
                                                                             .replace("{column}", columnName)
                                                                             .replace("{comment}", columnComment);
                            commentList.add(comment);
                        }
                        if(isFirst) isFirst = false;
                    }
                    
                    if(!pkList.toString().isEmpty()) {
                        registPk = PK_LIST.replace("{pkName}", table+"_PK")
                                                         .replace("{pkList}", pkList.toString().substring(0, pkList.toString().lastIndexOf(COMMA)));
                        inSetData.append(COMMA);
                        inSetData.append(registPk);
                    }
                    
                    inSetData.append(CLOSE_PAR);
                    log.info("CREATE TABLE " + table + inSetData.toString().replace(SPACE + COMMA, COMMA + CRLF));
                    
                    adminScanDao.createTable(table, inSetData.toString().replace(SPACE + COMMA, COMMA + CRLF));
                    for(String comment : commentList) {
                        log.info(comment);
                        adminScanDao.commentOn(comment);
                    }
                }
                
            }
            model.addObject("successLogs", successModel);
            model.addObject("site", true);
            return true;
        } catch(Exception e) {
            model.addObject("errorLogs", e.getMessage());
            return false;
        }
               
    }
    
    //DB로컬 데이터 불러오는 시스템(깃으로 DB공유하기 위해서)
    public boolean procSecondPhase(ModelAndView model) {
        // TODO Auto-generated method stub
        List<String> successModel = new ArrayList<String>();
        try {
            log.info("DATA MANAGEMENT SYSTEM");
            model.addObject("successLogs", successModel);
            model.addObject("site", false);
        } catch(Exception e) {
            model.addObject("errorLogs", e.getMessage());
            return false;
        }
        return true;
    }

    //DB데이터 로컬에 저장하는 시스템(깃으로 DB공유하기 위해서)
    public boolean procSavingData(ModelAndView model) {
        // TODO Auto-generated method stub
        List<String> successModel = new ArrayList<String>();
        try {
            log.info("TSV MANAGEMENT SYSTEM");
            for(String table : DaoManagement.Daos) {
                if(!CodeMap.isStringEqual(adminScanDao.isTableExisting(table))){
                    continue;
                }
                successModel.add("TABLE SAVE " + table + " END");
                List<String> columnNames = adminScanDao.getTableColumns(table);
                StringBuffer csvSb = new StringBuffer();
                StringBuffer tsvSb = new StringBuffer();
                boolean isFirst = true;
                //컬럼 삽입
                for(String columnName : columnNames) {
                  //첫 칼럼이 아니라면 콤마 삽입
                    if(!isFirst) {
                        csvSb.append(COMMA);
                        tsvSb.append(TAB);
                    }
                    if(columnName.contains("(PK)")) {
                        csvSb.append(columnName.substring(0, columnName.indexOf(OPEN_PAR)));
                    } else {
                        csvSb.append(columnName);
                    }
                    tsvSb.append(columnName);
                    isFirst = false;
                }
                log.info(csvSb.toString());
                List<Map<String, Object>> datas = adminScanDao.getTableDatas(table, csvSb.toString());
                tsvSb.append(CRLF);
                for(Map<String, Object> data : datas) {
                    isFirst = true;
                    for(String columnName : columnNames) {
                        //첫 칼럼이 아니라면 콤마 삽입
                          if(!isFirst) {
                              tsvSb.append(TAB);
                          }
                          if(columnName.contains("(PK)")) {
                              columnName = columnName.substring(0, columnName.indexOf(OPEN_PAR));
                          } 
                          tsvSb.append(data.get(columnName).toString());
                          
                          isFirst = false;
                      }
                    tsvSb.append(CRLF);
                }
                log.info(tsvSb.toString());
                SimpleDateFormat dateFormat = new SimpleDateFormat ( "yyyyMMddHHmmss");
                String sysdate = dateFormat.format(System.currentTimeMillis());
                File directory = new File("db/"+ table + SLASH );
                File old_file = new File("db/"+ table + SLASH + table + TSV_FILE_EXTENSION);
                File new_file = new File("db/"+ table + SLASH  +table + TSV_FILE_EXTENSION);
                if(!directory.exists()) {
                    directory.mkdirs();
                }
                if(!old_file.exists()) {
                    new_file.createNewFile();
                } else {
                    old_file.renameTo(new File("db/"+ table + SLASH  + "BK_"+ table + UNDER_BAR + sysdate + TSV_FILE_EXTENSION));
                }
                OutputStream os = new FileOutputStream(new_file);
                OutputStreamWriter osw = new OutputStreamWriter(os);
                BufferedWriter bsw = new BufferedWriter(osw);
                bsw.write(tsvSb.toString());
                bsw.flush();
                
                bsw.close();
                osw.close();
                os.close();
            }
            model.addObject("successLogs", successModel);
            model.addObject("site", false);
        } catch(Exception e) {
            model.addObject("errorLogs", e.getMessage());
            return false;
        }
        return true;
    }

}
