package com.attendance.pro.other;

import static com.attendance.pro.other.CodeMap.isEmpty;
import static com.attendance.pro.other.CodeMap.isEqual;
import static com.attendance.pro.other.CodeMap.isEqualMultyisOne;
import static com.attendance.pro.other.CodeMap.isStringEqual;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    public static final String NOT_NULL = "NOT NULL";
    public static final String NULL = " NULL";
    public static final String AND = " AND";
    public static final String OR = " OR";
    public static final String YES = "Y";
    public static final String NO = "N";
    public static final String PK = "PK";
    public static final String DEFAULT = "DEFAULT";
    public static final String COMMENT = "{table}.{column} IS '{comment}'";
    public static final String ALTER_MODIFY_TBL = "{table} MODIFY ({column} {change})";
    public static final String ALTER_DROP_TBL = "{table} DROP COLUMN {column}";
    public static final String ALTER_ADD_TBL = "{table} ADD {column}";
    public static final String PK_LIST = "CONSTRAINT {pkName} PRIMARY KEY( {pkList} )";
    public static final String TSV_FILE_EXTENSION = ".tsv";
    public static final String COLUMN_EQ_DATA = "{column} = '{data}'";
    
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
                    //컬럼명 변경은 제공하지 않음(이유 -> 어떤 컬럼이 변경되었는지 확인 불가 만약 변경을 할거라면 그 로직은 후에 추가)
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
                                String originComment = BLANK;
                                if(columnAttribute.indexOf(DOB_QUO)!=-1) {
                                    //매니지먼트에 코멘트가 존재하는 경우
                                    originComment = columnAttribute.substring(columnAttribute.indexOf(DOB_QUO)+1
                                            , columnAttribute.lastIndexOf(DOB_QUO));
                                    //일치하지 않는다면 변환해야므로 취득
                                    if(!isEqual(originComment , info.get("COMMENTS"))){
                                        alterComment = getComment(table, columnName, originComment);
                                    }
                                } else {
                                    //존재하지 않는 경우
                                    if(!isEmpty(info.get("COMMENTS"))){
                                        //DB에는 존재하는 경우 삭제처리를 위해 '' 데이터 입력
                                        alterComment = getComment(table, columnName, BLANK);
                                    }
                                }
                                
                                //2. 속성 Not null 취득(PK도 NOT NULL취급) 
                                if(columnAttribute.contains(NOT_NULL) || columnAttribute.contains(PK)) {
                                    //매니지먼트에 NOT NULL이 존재하는 경우
                                    if(!isEqual(NO , info.get("NULLABLE"))){
                                        //DB에는 NO일 경우 NOT_NULL설정
                                        //table modify (column_name not null)
                                        alterNotNull = getAlterModifyForm(table, columnName, NOT_NULL);
                                    }
                                } else {
                                    //매니지먼트에 NOT NULL이 존재하지 않는 경우
                                    if(!isEqual(YES , info.get("NULLABLE"))){
                                        //DB에는 YES일 경우 NULL설정
                                        //table modify (column_name null)
                                        alterNotNull = getAlterModifyForm(table, columnName, NULL);
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
                                        //table modify (column_name default column_default)
                                        alterDefault = getAlterModifyForm(table, columnName, DEFAULT  + SPACE + columnDefault);
                                    }
                                    
                                } else {
                                    //매니지먼트에 DEFAULT가 존재하지 않는 경우
                                    if(!isEqual(null , info.get("DATA_DEFAULT"))){
                                        //DB에는 NO일 경우 NOT_NULL설정
                                        //table modify (column_name default null)
                                        alterDefault = getAlterModifyForm(table, columnName, DEFAULT  + SPACE + NULL);
                                    }
                                }

                                //4. 속성 DB타입취득 (속성은 필수이므로 없는것은 존재하지 않음)
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
                                    //table modify (column_name dbtype)
                                    alterDBType = getAlterModifyForm(table, columnName, dbType);
                                }
                                
                            } else {
                                //존재하지 않는다면 존재하는 데이터가 있는지 확인
                                continue;
                            }
                           
                            //처리 완료후(수정이 되어 있는 경우) => ALTER TABLE개시
                            if(!isEqual(alterComment, BLANK)) {
                                adminScanDao.commentOn(alterComment);
                                log.info("COMMANT ON " + alterComment +" END");
                                successModel.add("COMMANT ON " + alterComment +" END");
                            }
                            if(!isEqual(alterNotNull, BLANK)) {
                                adminScanDao.alterTable(alterNotNull);
                                log.info("ALTER TABLE " +alterNotNull +" END");
                                successModel.add("ALTER TABLE " +alterNotNull +" END");
                            }
                            if(!isEqual(alterDefault, BLANK)) {
                                adminScanDao.alterTable(alterDefault);
                                log.info("ALTER TABLE " +alterDefault +" END");
                                successModel.add("ALTER TABLE " +alterDefault +" END");
                            }
                            if(!isEqual(alterDBType, BLANK)) {
                                adminScanDao.alterTable(alterDBType);
                                log.info("ALTER TABLE " +alterDBType +" END");
                                successModel.add("ALTER TABLE " +alterDBType +" END");
                                
                            }
                            
                        }
                        
                        //매니지먼트에서 삭제가 된 DB테이블 컬럼
                        if(!isNameExist) {
                            String alterDropColumn =getAlterDropForm(table, info.get("COLUMN_NAME"));
                            adminScanDao.alterTable(alterDropColumn);
                            log.info("ALTER TABLE " +alterDropColumn +" END");
                            successModel.add("ALTER TABLE " + alterDropColumn +" END");
                        }
                        
                    }
                    
                    //신규 칼럼이 존재할 경우
                    if(columnInfos.size()<columns.length) {
                        for(String column : columns) {
                            String columnName = column.split(COLONE)[0].trim();
                            String columnAttribute = column.split(COLONE)[1].trim();
                            if(exceptColumn(columnName)) {
                                continue;
                            }
                            if(isEmpty(newColumnsCheck.get(columnName))) {
                                //신규 컬럼 추가 로직
                                //1. 속성 코멘트 확인
                                String columnComment = BLANK;
                                if(columnAttribute.indexOf(DOB_QUO)!=-1) {
                                    columnComment = columnAttribute.substring(columnAttribute.indexOf(DOB_QUO)+1
                                            , columnAttribute.lastIndexOf(DOB_QUO));
                                }
                                //2. 공통처리
                                String alterAddColumn = getAlterAddForm(table, getAttributeInColumns(columnName, columnAttribute));
                                adminScanDao.alterTable(alterAddColumn);
                                log.info("ALTER TABLE " +alterAddColumn +" END");
                                successModel.add("ALTER TABLE " + alterAddColumn +" END");

                                //3. 코멘트 추가
                                if(!columnComment.equals(BLANK)) {
                                    String comment = getComment(table, columnName, columnComment);
                                    adminScanDao.commentOn(comment);
                                    log.info("COMMANT ON " + comment +" END");
                                    successModel.add("COMMANT ON " + comment +" END");
                                }
                                
                            }
                            
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
                            columnComment = columnAttribute.substring(columnAttribute.indexOf(DOB_QUO)+1
                                    , columnAttribute.lastIndexOf(DOB_QUO));
                        }
                        if(!columnComment.equals(BLANK)) {
                            String comment = getComment(table, columnName, columnComment);
                            commentList.add(comment);
                        }
                        //2. 속성 PK 취득
                        if(columnAttribute.contains(PK)) {
                            pkList.append(columnName);
                            pkList.append(COMMA);
                        }
                        //3. 기타 속성들 취득
                        String attribute = getAttributeInColumns(columnName, columnAttribute);
                        inSetData.append(attribute);
                        if(isFirst) isFirst = false;
                    }
                    
                    //4. PK들을 하나로 모아 일괄적으로 PK설정
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
    /**
     * 코멘트 양식을 리턴한다.
     * @param table
     * @param columnName
     * @param columnComment
     * @return table.column_name IS 'column_comment';
     */
    private String getComment(String table, String columnName, String columnComment) {
        return COMMENT.replace("{table}", table)
                .replace("{column}", columnName)
                .replace("{comment}", columnComment);
    }
    /**
     * 테이블 변경 칼럼 변경 양식을 리턴한다
     * @param table
     * @param columnName
     * @param typeAttribute
     * @return table MODIFY (column_name type_attribute)"
     */
    private String getAlterModifyForm(String  table, String columnName, String typeAttribute) {
        return ALTER_MODIFY_TBL.replace("{table}", table)
                .replace("{column}", columnName)
                .replace("{change}", typeAttribute);
    }
    /**
     * 테이블 변경 칼럼 추가 양식을 리턴한다
     * @param table
     * @param column
     * @return table ADD COLUMN column"
     */
    private String getAlterAddForm(String  table, String column) {
        return ALTER_ADD_TBL.replace("{table}", table)
                .replace("{column}", column);
    }
    /**
     * 테이블 변경 칼럼 삭제 양식을 리턴한다
     * @param table
     * @param columnName
     * @return table DROP COLUMN column_name"
     */
    private String getAlterDropForm(String  table, String columnName) {
        return ALTER_DROP_TBL.replace("{table}", table)
                .replace("{column}", columnName);
    }
    
    /**
     * 컬럼 = 데이터 양식으로 리턴한다
     * @param column
     * @param data
     * @return column = data
     */
    private String getColumnEqualData(String column, String data) {
        return COLUMN_EQ_DATA.replace("{column}", column)
                .replace("{data}", data);
    }
    
    /**
     * 공통 속성 처리(코멘트, PK뺌)
     * @param columnName
     * @param columnAttribute
     * @return column_name dbtype (default column_default) (not null) 
     */
    private String getAttributeInColumns(String columnName, String columnAttribute) {
        //1. 속성 Not null 취득
        boolean isNotNull = false;
        if(columnAttribute.contains(NOT_NULL)) {
            isNotNull = true;
        }
        
        //2.DEFAULT값 취득
        boolean isDefault = false;
        String columnDefault = BLANK;
        if(columnAttribute.contains(DEFAULT)) {
            columnDefault = columnAttribute.substring(columnAttribute.indexOf(DEFAULT)+DEFAULT.length() +1
                            , columnAttribute.indexOf(CLOSE_PAR, columnAttribute.indexOf(DEFAULT)));
            isDefault = true;
        }
        
        //3. 속성 DB타입취득
        int spaceIndex = columnAttribute.indexOf(SPACE);
        String dbType = columnAttribute;
        if(spaceIndex!=-1) {
            dbType = columnAttribute.substring(0, spaceIndex);
        }
        StringBuffer returnSb = new StringBuffer();
        
        //4. 아래와 같이 입력후 리턴
        //리턴값 column_name dbtype (default column_default) (not null) 
        returnSb.append(columnName);
        returnSb.append(SPACE);
        returnSb.append(dbType);
        returnSb.append(SPACE);
        if(isDefault) {
            returnSb.append(DEFAULT);
            returnSb.append(SPACE);
            returnSb.append(columnDefault);
            returnSb.append(SPACE);
        }
        if(isNotNull) {
            returnSb.append(NOT_NULL);
            returnSb.append(SPACE);
        }
        return returnSb.toString();
    }

    //DB로컬 데이터 불러오는 시스템(깃으로 DB공유하기 위해서)
    //삭제된 데이터는 찾아오지 않음(DB는 계속해서 더하거나 바꾸기만 할 뿐임)
    public boolean procSecondPhase(ModelAndView model) {
        // TODO Auto-generated method stub
        List<String> successModel = new ArrayList<String>();
        try {
            log.info("DATA MANAGEMENT SYSTEM");
            for(String table : DaoManagement.Daos) {
                if(!CodeMap.isStringEqual(adminScanDao.isTableExisting(table))){
                    continue;
                }
                File directory = new File("db/"+ table + SLASH );
                File load_file = new File("db/"+ table + SLASH + table + TSV_FILE_EXTENSION);
                //테이블 폴더가 존재하지 않거나 데이터 파일이 존재하지 않는경우 다음으로 넘긴다.
                if(!directory.exists()) {
                    continue;
                }
                if(!load_file.exists()) {
                    continue;
                } 
                InputStream os = new FileInputStream(load_file);
                InputStreamReader isr = new InputStreamReader(os);
                BufferedReader br = new BufferedReader(isr);
                //칼럼명 취득
                List<String> columnNames = adminScanDao.getTableColumns(table);
                //파일의 칼럼명이 csv와 일치하는지 확인
                String columnLine = br.readLine();
                String[] columns = columnLine.split(TAB);
                if(columnNames.size()!=columns.length) {
                    continue;
                }
                Map<String, Boolean> columnsConfirm = new HashMap<String, Boolean>();
                for(String columnName : columnNames) {
                    columnsConfirm.put(columnName, true);
                }
                boolean isEqualColumns = true;
                //csv칼럼과 비교시 일치하지 않으면
                List<String> pkList  = new ArrayList<String>();
                int pkIndex = 0;
                for(String column : columns) {
                    pkIndex++;
                    if(column.contains(PK)) {
                        column = column.replace(OPEN_PAR+PK+CLOSE_PAR, BLANK);
                        pkList.add(pkIndex+COMMA+column);
                    }
                    if(isEmpty(columnsConfirm.get(column))) {
                        log.debug("column is nothing " + column + COMMA + columnsConfirm);
                        isEqualColumns = false;
                        
                    }
                }
                if(!isEqualColumns) {
                    continue;
                }
                
                //전부 일치하여 데이터를 넣을 준비
                String dataLine = null;
                int insertLineCnt = 0;
                int updateLineCnt = 0;
                while((dataLine = br.readLine()) != null) {
                    //PK항목 조사
                    String[] datas = dataLine.split(TAB);
                    StringBuffer wherePks = null;
                    StringBuffer updateDatas = null;
                    Map<String, String> tsvDatas = new HashMap<String, String>();
                    for(int i = 0; i < datas.length; i++){
                        boolean isPk = false;
                        for(String pk : pkList) {
                            String[] indexColumns = pk.split(COMMA);
                            int index = Integer.valueOf(indexColumns[0]);
                            if(i == index-1) {
                                if(wherePks == null) {
                                    wherePks = new StringBuffer();
                                } else {
                                    wherePks.append(SPACE+AND+SPACE);
                                }
                                isPk = true;
                                wherePks.append(getColumnEqualData(indexColumns[1],datas[i]));
                            }
                        }
                        if(!isPk) {
                            if(updateDatas == null) {
                                updateDatas = new StringBuffer();
                            } else {
                                updateDatas.append(COMMA);
                            }
                            updateDatas.append(getColumnEqualData(columnNames.get(i),datas[i]));
                        }
                        tsvDatas.put(columnNames.get(i) , datas[i]);
                    }
                    //pk로 데이터 찾기
                    Map<String, Object> dbDatas = adminScanDao.getTableDataByColumnsPk(table, wherePks.toString());
                    
                    //일치하는 데이터가 없다면 = insert
                    if(dbDatas == null || dbDatas.isEmpty()) {
                        adminScanDao.insertData(table, columnNames, datas);
                        insertLineCnt++;
                    } else {
                        //일치하는 데이터가 있다면 = update(?)
                        //일치하는 데이터의 값이 일치하지 않다면 = update
                        boolean isUpdate = false;
                        for(String column : columnNames) {
                            String dbData = dbDatas.get(column).toString();
                            if(!isEqual(dbData , tsvDatas.get(column))) {
                                isUpdate = true;
                            }
                        }
                        if(!isUpdate) {
                            adminScanDao.updateData(table, updateDatas.toString(), wherePks.toString());
                            updateLineCnt++;
                        }
                        //전 값이 일치하다면 그냥 넘어감
                    }
                    
                }
                br.close();
                isr.close();
                os.close();
                successModel.add("update data : " + updateLineCnt);
                successModel.add("insert data : " + insertLineCnt);
            }
            
            
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
