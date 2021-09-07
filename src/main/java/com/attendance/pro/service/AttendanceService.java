package com.attendance.pro.service;

import static com.attendance.pro.other.CodeMap.ERROR;
import static com.attendance.pro.other.CodeMap.MSG;
import static com.attendance.pro.other.CodeMap.RES;
import static com.attendance.pro.other.CodeMap.RESULT;
import static com.attendance.pro.other.CodeMap.SUCCESS;
import static com.attendance.pro.other.CodeMap.isEqual;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.attendance.pro.dao.AttendanceDao;
import com.attendance.pro.dto.AttendanceDto;
import com.attendance.pro.dto.UserDto;

@Service
public class AttendanceService extends BaseService {

    private Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final int goToWork         = 1;  //출근
    private final int offWork             = 2;  //퇴근
    private final int earlyDeparture = 3;  //조퇴
    private final int breakTime         = 4;  //휴식

    @Autowired
    private AttendanceDao attendanceDao = null;
    
    @Override
    public Map<String, Object> proc(Map<String, Object> data, Map<String, Object> resData, String... props){
        ArrayList<String> msgList = new ArrayList<String>();
        String action = String.valueOf(data.get("action"));
        String userCd = props[0];
        try {
            if(isEqual(action, "1")) {
                //Check시에
                return resData = attendanceCheck(userCd, data, resData);
            } else if(isEqual(action, "2")) {
                //Check용 액션을 불러오므로.
                data.put("action", 1);
                String result = objectToString(data.get(RESULT));
                if(isResultDatasEqual(result, data, userCd)) {
                    //이후 완료처리
                    data.put("action", 2);
                    return resData = attendanceProc(userCd, data, resData);
                } else {
                    msgList.add("데이터가 올바르지 않습니다. 다시 처리해 주세요.");
                    resData.put(RES, ERROR);
                    resData.put(MSG, msgList);
                    return resData;
                }
                //확정 등록시에
            } else {
                return resData;
            }
        } catch(Exception e) {
            msgList.add("출결이 처리되지 않았습니다. 다시 시도해 주세요.");
            resData.put(RES, ERROR);
            resData.put(MSG, msgList);
            if(e instanceof SQLException) {
                SQLException exception = (SQLException) e;
                errorRegistSystem(data, "Attendance", userCd, String.valueOf(exception.getErrorCode()), exception.getMessage());
            } else {
                errorRegistSystem(data, "Attendance", userCd, "APERR", e.getMessage());
            }
            return resData;
        }
        
    }

    /**
     * 유저 출결 서비스
     * @param userCd
     * @param data
     * @param resData
     * @return
     */
    public Map<String, Object> attendanceProc(String userCd, Map<String, Object> data, Map<String, Object> resData) throws Exception {
        log.info("====Attendance Process Start====");
        List<String> msgList = new ArrayList<String>();
        //오늘 날짜의 유저 seq를 불러온다
        //log.info(userCd);
        Integer todaysSeq = attendanceDao.getAttendanceSeq(userCd);
        if(todaysSeq==null || todaysSeq==0) {
            todaysSeq = 1;
        } else {
            todaysSeq = todaysSeq+1;
        }
        AttendanceDto dto = dataToDto(data);
        //등록 유저 정보 입력
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        String nowTime = sdf.format(date);
        dto.setAttendanceSeq(todaysSeq);
        dto.setAttendanceDate(date);
        dto.setUserDto(new UserDto());
        dto.getUserDto().setUserCd(userCd);
        //출결 데이터가 휴식이라면(지난 출결 데이터가 휴식인지 확인
        if(dto.getAttendanceType()==breakTime) {
            AttendanceDto breakDto = attendanceDao.getNewestAttendance(userCd);
            //휴식중인 상태인지 확인.
            if(breakDto.getAttendanceType()==4 && breakDto.getAttendanceStatus() == 0) {
                //휴식중인 상태라면 휴식 끝을 넣어준다.
                dto.setAttendanceStatus(1);
            }
        }
        //출결 데이터 등록
        Integer result = attendanceDao.registAttendance(dto);
        if(result == 0) {
            throw new Exception();
        }
        String typeMsg = "";
        String successMsg = "현재 시간 %t에 %s 하셨습니다.";
        int attType = dto.getAttendanceType();
        typeMsg = getStringType(attType);
        msgList.add(successMsg.replace("%s", typeMsg).replace("%t", nowTime));
        checkRegistSystem(data, "Attendance Proc", userCd);
        resData.put(RES, SUCCESS);
        resData.put(MSG, msgList);
        log.info("====Attendance Process End====");
        return resData;
    }

    private AttendanceDto dataToDto(Map<String, Object> data) {
        AttendanceDto dto = new AttendanceDto();
        dto.setAttendanceType(objectToInteger(data.get("attendance_type")));
        dto.setLatitude(objectToString(data.get("latitude")));
        dto.setLongitude(objectToString(data.get("longitude")));
        dto.setPlaceInfo(objectToString(data.get("place_info")));
        dto.setTerminal(objectToString(data.get("terminal")));
        dto.setErrorCd(objectToString(data.get("error_cd")));
        dto.setErrorMsg(objectToString(data.get("error_msg")));
        return dto;
    }
    
    private String getStringType(int type) {
        switch(type) {
            case goToWork :
                return "출근";
            case offWork :
                return "퇴근";
            case earlyDeparture :
                return "조퇴";
            case breakTime :
                return "휴식";
        }
        return "출근"; 
    }

    /**
     * 출결 화면 기동시
     * @param userCd
     * @param resData
     * @return
     */
    public Map<String, Object> getAttendanceData(String userCd, Map<String, Object> resData) throws Exception {
        log.info("====Attendance Window Start====");
        log.info(userCd);
        Map<String, String> datas = new HashMap<String, String>();
        AttendanceDto dto = attendanceDao.getNewestAttendance(userCd);
        String attendanceSts = "출근 대기";
        String alertMsg = "";
        //최근 한달간의 출퇴근 데이터가 존재하지 않으면
        if(dto == null || dto.getAttendanceType()==null) {
            //출근 대기로 값을 보냄
            datas.put("att_sts", attendanceSts);
            resData.put("datas", datas);
            return resData;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MM,dd");
        SimpleDateFormat regTimeFormat = new SimpleDateFormat("MM/dd HH:mm:ss");
        //시간 계산
        Date regDate = dto.getAttendanceRegDate();
        Date nowDate = new Date();
        long regNowDiff = (nowDate.getTime() - regDate.getTime()) / 1000; //밀리초
        long regNowDiffDays = regNowDiff / (60*60); //초, 분
        String attRegTime = "";
        //출근 상태이나 퇴근을 안찍은 출근 상태일 경우
        int attType = dto.getAttendanceType();
        switch(attType) {
        case goToWork :
            attendanceSts = "출근 중";
            attRegTime = regTimeFormat.format(regDate);
            //두 시간사이가 24시간(하루)가 지났을 경우
            if(regNowDiffDays > 24) {
                alertMsg = "출근한지 하루가 지났습니다! 퇴근을 찍어주세요";
            }
            break;
        case offWork :
          //퇴근 완료가 오늘 날짜라면 퇴근 완료, 아니면 출근 대기상태로 유지
            if(isEqual(sdf.format(regDate), sdf.format(nowDate))) {
                attendanceSts = "퇴근 완료";
                attRegTime = regTimeFormat.format(regDate);
            }
            break;
        case earlyDeparture :
          //조퇴 완료가 오늘 날짜라면 조퇴 완료, 아니면 출근 대기상태로 유지
            if(isEqual(sdf.format(regDate), sdf.format(nowDate))) {
                attendanceSts = "조퇴 완료";
                attRegTime = regTimeFormat.format(regDate);
            }
            break;
        case breakTime :
            attendanceSts = "출근 중(휴식)";
            attRegTime = regTimeFormat.format(regDate);
            if(regNowDiffDays > 24) {
                alertMsg = "휴식한지 하루가 지났습니다! 휴식 완료를 찍어주세요";
            }
            //상태가 휴식완료(1)일 경우
            if(isEqual(dto.getAttendanceStatus(), 1)) {
                attendanceSts = "출근 중(휴식 완료)";
                //가장 최근의 출근 데이터 취득 후 출근한지 얼마나 되었는지 확인
                AttendanceDto attendData = attendanceDao.getNewestAttendData(userCd);
                Date attendDate = attendData.getAttendanceRegDate();
                long attNowDiff = (nowDate.getTime() - attendDate.getTime()) / 1000; //밀리초
                long attNowDiffDays = attNowDiff / (60*60); //초, 분
                attRegTime = regTimeFormat.format(attendDate);
                if(attNowDiffDays > 24) {
                    alertMsg = "출근한지 하루가 지났습니다! 퇴근을 찍어주세요";
                }
            }
            break;
        } 
        
        datas.put("att_time", attRegTime);
        datas.put("att_sts", attendanceSts);
        datas.put("att_msg", alertMsg);
        resData.put("datas", datas);
        log.info("====Attendance Window End====");
        return resData;
    }

    /**
     * 출결 체크서비스
     * @param userCd
     * @param data
     * @param resData
     * @return
     */
    public Map<String, Object> attendanceCheck(String userCd, Map<String, Object> data, Map<String, Object> resData) throws Exception {
        log.info("====Attendance Check Start====");
        List<String> msgList = new ArrayList<String>();
        int errCd = 0;
        //가장 최근의 출결데이터를 취득한다. [락을 건다]
        AttendanceDto newData = attendanceDao.getNewestAttendance(userCd);
        AttendanceDto reqData = dataToDto(data);
        int attType = reqData.getAttendanceType();
        //가장 최근의 데이터가 존재하지 않을 경우
        //출근만 허용한다
        if(newData==null) {
            if(attType!=goToWork) {
                errCd = 5;
                msgList.add("출근 전이므로 퇴근/조퇴/휴식을 할 수 없습니다.");
            }
        } else {
            //가장 최근의 데이터가 존재하는 경우
            int newType = newData.getAttendanceType();
            //가장 최근의 출결데이터와 요청한 데이터의 타입이 일치하다면(같은 출근이나 같은 퇴근반복)
            //휴식의 경우 상관없음
            if(isEqual(newData.getAttendanceType(),reqData.getAttendanceType())) {
                switch(attType) {
                case goToWork :
                    errCd = 1;
                    msgList.add("이미 출근 중입니다. 출근을 덮어쓸까요?");
                    break;
                case offWork :
                    errCd = 2;
                    msgList.add("이미 퇴근 중입니다. 퇴근을 덮어쓸까요?");
                    break;
                case earlyDeparture :
                    errCd = 3;
                    msgList.add("이미 조퇴하셨습니다. 조퇴를 덮어쓸까요?");
                    break;
                }
            }
            //타입 비교
            //출근은 같은 날의 퇴근, 조퇴기록이 있을 시 재 출근 여부를 물어봐야함. 이전 기록이 휴식이면 출근 불가능
            //퇴근, 조퇴는 이전 기록이 출근이어야함(휴식일 경우 휴식끝(1)이어야함)
            //휴식의 경우 이전 기록이 휴식이거나 출근이어야함
            if(errCd == 0) {
                switch(attType) {
                case goToWork :
                    SimpleDateFormat sdf = new SimpleDateFormat("MM,dd");
                    if((newType==offWork || newType==earlyDeparture) &&
                            isEqual(sdf.format(newData.getAttendanceRegDate()), sdf.format(new Date()))) {
                        errCd = 4;
                        String typeMsg = getStringType(newType);
                        msgList.add("이미 %s 완료중입니다. 재출근을 하시겠습니까?".replace("%s", typeMsg));
                    } else {
                        if(newType==breakTime) {
                            errCd = 8;
                            String typeMsg = getStringType(newType);
                            msgList.add("%s 처리가 되어 있으므로 퇴근을 하셔야 재 출근이 가능합니다.".replace("%s", typeMsg));
                        }
                    }
                    break;
                case offWork :
                case earlyDeparture :
                    if(!(newType==goToWork || 
                        (newType==breakTime && newData.getAttendanceStatus()==1))) {
                        errCd = 6;
                        String typeMsg = getStringType(attType);
                        msgList.add("출근 중이 아니기 때문에 %s 하실 수 없습니다.".replace("%s", typeMsg));
                    }
                    break;
                case breakTime :
                    if(!(newType==goToWork || 
                            newType==breakTime)) {
                        errCd = 7;
                        String typeMsg = getStringType(attType);
                        String typeMsg2 = getStringType(newType);
                        msgList.add("%s1 하셨으므로 %s2 이 불가능합니다.".replace("%s1", typeMsg2)
                                .replace("%s2", typeMsg));
                    }
                    break;
                }
            }
        }
        String result = checkRegistSystem(data, "Attendance Check", userCd);
        resData.put(RESULT, result);
        if(errCd==0) {
            resData.put(RES, SUCCESS);
        } else {
            resData.put(RES, ERROR);
            resData.put("err_cd", errCd);
        }
        resData.put(MSG, msgList);
        log.info("====Attendance Check End====");
        return resData;
    }

}
