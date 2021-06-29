package com.attendance.pro.other;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    public static final String PK = "pk";
    public static final String DEFAULT = "default";
    public static final String COMMENT = "{table}.{column} IS '{comment}'";
    public static final String PK_LIST = "CONSTRAINT {pkName} PRIMARY KEY( {pkList} )";
    public static final String TSV_FILE_EXTENSION = ".tsv";
    //public static final String FK = "Foreign Key";
    

    @Autowired
    AdminScanDao adminScanDao;
    
    //DB테이블 등록 시스템
    public boolean proc(ModelAndView model) {
        List<String> successModel = new ArrayList<String>();
        try {
            log.info("TABLE MANAGEMENT SYSTEM");
            //Management에 등록되어있는 테이블이 있는지 참조
            for(String table : DaoManagement.Daos) {
                if(CodeMap.isStringEqual(adminScanDao.isTableExisting(table))){
                    //테이블이 DB에 존재한다면
                    //해당 테이블의 내용이 수정이 되어있는지 컬럼확인
                    log.info(table + " is Exist");
                    //테이블이 수정이 되었다면
                    successModel.add("ALTER TABLE " + table + " END");
                } else {
                    successModel.add("CREATE TABLE " + table + " END");
                    //테이블이 존재하지 않으면
                    //테이블을 새로 만들기 위해서 테이블 정보 취득
                    Map<String, String> map = DaoManagement.getColumns();
                    String data = map.get(table);
                    String[] columns = data.replace(OPEN_BRA,BLANK)
                                                            .replace(CLOSE_BRA, BLANK)
                                                            .split(COMMA);
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
                            columnComment = columnAttribute.substring(columnAttribute.indexOf(DOB_QUO)+1
                                    , columnAttribute.lastIndexOf(DOB_QUO));
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
