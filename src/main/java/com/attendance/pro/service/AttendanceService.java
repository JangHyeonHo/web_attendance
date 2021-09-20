package com.attendance.pro.service;

import static com.attendance.pro.other.CodeMap.ERROR;
import static com.attendance.pro.other.CodeMap.MSG;
import static com.attendance.pro.other.CodeMap.RES;
import static com.attendance.pro.other.CodeMap.RESULT;
import static com.attendance.pro.other.CodeMap.SUCCESS;
import static com.attendance.pro.other.CodeMap.getMsg;
import static com.attendance.pro.other.CodeMap.isEqual;
import static com.attendance.pro.other.CodeMap.isEqualMultyisOne;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.attendance.pro.dao.AttendanceDao;
import com.attendance.pro.dao.ScheduleManagementDao;
import com.attendance.pro.dto.AttendanceDto;
import com.attendance.pro.dto.HolidayValuesDto;
import com.attendance.pro.dto.ScheduleManagementDto;
import com.attendance.pro.dto.UserDto;
import com.attendance.pro.response.AttendanceDetailResData;

/**
 * 출결 처리 서비스(패치 시급함)
 * @author jang
 *
 */
@Service
public class AttendanceService extends BaseService {

    private Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final int goToWork         = 1;  //출근
    private final int offWork             = 2;  //퇴근
    private final int earlyDeparture = 3;  //조퇴
    private final int breakTime         = 4;  //휴식

    @Autowired
    private AttendanceDao attendanceDao = null;
    
    @Autowired
    private ScheduleManagementDao scheduleManagementDao = null;
    
    /**
     * 출결 처리 서비스
     * @param data 요청 받은 데이터
     * @param resData 반환할 데이터
     * @param windowData 화면 값(언어설정)
     * @param props 기타값
     */
    @Override
    public Map<String, Object> proc(Map<String, Object> data, Map<String, Object> resData, Map<String, String> windowData, String... props){
        log.debug("====Attendance Proc Open====");
        String userCd = props[0];
        log.debug("userCd : " + userCd);
        ArrayList<String> msgList = new ArrayList<String>();
        //처리 요청을 확인한다. 1 = 체크로직, 2 = 확정로직
        String action = String.valueOf(data.get("action"));
        try {
            //체크로직
            if(isEqual(action, "1")) {
                resData = attendanceCheck(userCd, data, resData, windowData);
            //확정로직
            } else if(isEqual(action, "2")) {
                //Check용 액션을 불러오므로.
                data.put("action", 1);
                String result = objectToString(data.get(RESULT));
                //데이터 변조가 없는지 확인(체크시와 확정시가 일치한지)
                if(isResultDatasEqual(result, data, userCd)) {
                    //이후 완료처리
                    data.put("action", 2);
                    resData = attendanceProc(userCd, data, resData, windowData);
                } else {
                    //데이터 변조가 되어있으면 에러 발생
                    msgList.add("데이터가 올바르지 않습니다. 다시 처리해 주세요.");
                    resData.put(RES, ERROR);
                    resData.put(MSG, msgList);
                }
            } else if(isEqual(action, "3")) {
              //데이터 불러오기 로직
                resData = getAttendanceDetailDatas(userCd, data, resData, windowData);
            }
            log.debug("====Attendance Proc Close====");
           return resData;
        } catch(Exception e) {
            //에러 처리
            msgList.add("출결이 처리되지 않았습니다. 다시 시도해 주세요.");
            resData.put(RES, ERROR);
            resData.put(MSG, msgList);
            log.debug("====Attendance Proc Error====");
            if(e instanceof SQLException) {
                SQLException exception = (SQLException) e;
                errorRegistSystem(data, "Attendance", userCd, String.valueOf(exception.getErrorCode()), exception.getMessage());
            } else {
                String errMsg = "";
                for(StackTraceElement st : e.getStackTrace()) {
                    errMsg += st.toString()+"\n";
                }
                errorRegistSystem(data, "Attendance", userCd, "APERR", errMsg);
            }
            return resData;
        }
        
    }
    /**
     * 출결 데이터 불러오기 서비스
     * @param userCd
     * @param data
     * @param resData
     * @param windowData
     * @return
     */
    private Map<String, Object> getAttendanceDetailDatas(String userCd, Map<String, Object> data,
            Map<String, Object> resData, Map<String, String> windowData) {
        log.debug("====Attendance Detail Service Open====");
        //상세화면에서 요청한 날짜를 취득한다.
        Integer years = objectToInteger(data.get("years"));
        Integer months = objectToInteger(data.get("months"));
        //오늘 날짜를 취득한다.
        Calendar todayDate = Calendar.getInstance();
        Date today = todayDate.getTime();
        //해당 날짜에 존재하는 스케쥴 값을 가져온다.
        Calendar selectedDate = Calendar.getInstance();
        //이번달
        selectedDate.set(years, months, 1, 0, 0, 0);
        Date nowDate = selectedDate.getTime();
        long nowDateMilis = selectedDate.getTimeInMillis();
        //다음달(이번달이 12월이라면 내년 1월 1일을 표시)
        if(months!=12) {
            selectedDate.set(years, months+1, 1);
        } else {
            selectedDate.set(years+1, 1, 1);
        }
        Date nextDate = selectedDate.getTime();
        long nextDateMilis = selectedDate.getTimeInMillis();
        long scheduleDays = ((nextDateMilis - nowDateMilis) / 1000) / (60*60);
        //다음달+2일까지(최대 2일까지)(이번달이 12월이라면 내년 1월 2일을 표시)
        if(months!=12) {
            selectedDate.set(years, months+1, 2);
        } else {
            selectedDate.set(years+1, 1, 2);
        }
        Date stampNextDate = selectedDate.getTime();
        List<ScheduleManagementDto> getSchedule = scheduleManagementDao.getNowMonthSchedule(userCd, nowDate, nextDate);
        //출결 상황 취득
        List<AttendanceDto> getStamp = attendanceDao.getNowMonthStamp(userCd, nowDate, stampNextDate);
        List<AttendanceDetailResData> scheduleResData = new ArrayList<AttendanceDetailResData>();
        boolean isAttend = false;
        boolean isEnd = false;
        int index = 0;
        //스케쥴 responseData DB송신
        for(ScheduleManagementDto sche : getSchedule) {
            AttendanceDetailResData schedule = new AttendanceDetailResData();
            HolidayValuesDto holidayChk = sche.getHolidayValuesDto();
            if(holidayChk!=null && holidayChk.getHolidaySeq() > 0) {
                schedule.setFixScheduleIn("");
                schedule.setFixScheduleOut("");
                schedule.setStampIn("");
                schedule.setStampOut("");
                schedule.setHoliday(true);
                scheduleResData.add(schedule);
                continue;
            }
            schedule.setFixScheduleIn(sche.getScheduleStartTime());
            schedule.setFixScheduleOut(sche.getScheduleEndTime());
            //출결 데이터를 마지막까지 확인을 하였을 때(이후 확인할 필요가 없기 때문에)
            if(isEnd) {
                schedule.setStampIn("");
                schedule.setStampOut("");
                schedule.setHoliday(true);
                scheduleResData.add(schedule);
                continue;
            }
            //출결상황이 존재하지 않을때.
            if(getStamp.isEmpty()) {
                schedule.setStampIn("");
                schedule.setStampOut("");
                scheduleResData.add(schedule);
                continue;
            }
            stamp : for(int i = index ; i < getStamp.size(); i++) {
                AttendanceDto stamp = getStamp.get(i);
                Integer attendType = stamp.getAttendanceType();
                //입력 시간이 오늘 날짜보다 이전일 경우
                if(stamp.getAttendanceDate().compareTo(sche.getScheduleDate()) < 0) {
                    //이전 날짜는 필요없으니 다음으로 넘김
                    continue;
                } else {
                    SimpleDateFormat sdf = new SimpleDateFormat("YYYYMMdd");
                    SimpleDateFormat timeSdf = new SimpleDateFormat("HHmm");
                    String scheFormat = sdf.format(sche.getScheduleDate());
                    String stampFormat = sdf.format(stamp.getAttendanceDate());
                    Calendar diffCal = Calendar.getInstance();
                    diffCal.setTime(stamp.getAttendanceDate());
                    long stamLong = diffCal.getTimeInMillis();
                    diffCal.setTime(sche.getScheduleDate());
                    long scheLong = diffCal.getTimeInMillis();
                    long diffTime = ((stamLong - scheLong)/1000) / (60*60);
                    //날짜가 같은 경우
                    /**
                     * 같은 날 출근 퇴근 출근의 경우 => 이런 경우는 없어야하는디....(만약 출근 퇴근 출근일 경우 앞의 출근 퇴근의 상태를 1로 만듬?)
                     * 출근 출근 퇴근
                     * 출근 퇴근 퇴근
                     * 출근 => 다음 날 퇴근
                     */
                    if(isEqual(scheFormat, stampFormat)) {
                        //출근 상태가 여부랑 상관없이 출근이 온다면
                        if(isEqual(attendType, goToWork)) {
                            if(!isAttend) isAttend=!isAttend; //출근 상태로 변환
                            schedule.setStampIn(timeSdf.format(stamp.getAttendanceDate())); //출근 시각 등록
                            index++;
                            //마지막 출근 데이터라면
                            if(i==getStamp.size()-1) {
                                schedule.setStampOut("");
                                isEnd = true;
                                break stamp;
                            }
                            //출근 상태이고 퇴근이라면
                        } else if(isAttend && isEqualMultyisOne(attendType, offWork, earlyDeparture)) {
                            isAttend=!isAttend; //퇴근 상태로 변환
                            schedule.setStampOut(timeSdf.format(stamp.getAttendanceDate())); //출근 시각 등록
                            index++;
                            //출근 상태가 아닌데 퇴근일 경우
                        } else if(!isAttend && isEqualMultyisOne(attendType, offWork, earlyDeparture)) {
                            //퇴근이 이미 찍힌 후라면
                            schedule.setStampOut(timeSdf.format(stamp.getAttendanceDate()));
                            index++;
                        }
                    } else {
                        //날짜가 같지 않을경우(즉 다음날일 경우)
                        if(!isAttend) {
                            //출근 상태가 아니라면
                            if(schedule.getStampIn()==null) {
                                schedule.setStampIn("");
                                if(schedule.getStampOut()==null) {
                                    schedule.setStampOut("");
                                }
                                break stamp;
                            } else {
                                //퇴근이 또 찍혀있다면?
                                if(isEqualMultyisOne(attendType, offWork, earlyDeparture)) {
                                    //퇴근 또 찍힌게 48시간 이후라면?
                                    if(diffTime > 48) {
                                        if(schedule.getStampOut()==null) {
                                            schedule.setStampOut("");
                                        }
                                        break stamp;
                                    }
                                    //다음날까지 일을 한 경우(24시간을 더하여 계산)
                                    String hhmm = String.valueOf(
                                            Integer.valueOf(
                                                    timeSdf.format(stamp.getAttendanceDate()))+2400);
                                    schedule.setStampOut(hhmm);
                                    index++;
                                } else {
                                    //출근일 경우
                                    break stamp;
                                }
                            }
                            //다음날인데 출근 연달아 찍혀있을 경우
                        } else if(isAttend && isEqual(attendType, goToWork)) {
                            //퇴근이 없는 걸로 간주하고 퇴근이라고 인식하게 한후 다음날 처리로 넘어감
                            schedule.setStampOut("");
                            isAttend = false;
                            break stamp;
                        } else if(isAttend && isEqualMultyisOne(attendType, offWork, earlyDeparture)) {
                            //퇴근 또 찍힌게 48시간 이후라면?
                            if(diffTime > 48) {
                                if(schedule.getStampOut()==null) {
                                    schedule.setStampOut("");
                                }
                                isAttend = false;
                                break stamp;
                            }
                            //다음날까지 일을 한 경우(24시간을 더하여 계산)
                            String hhmm = String.valueOf(
                                    Integer.valueOf(
                                            timeSdf.format(stamp.getAttendanceDate()))+2400);
                            schedule.setStampOut(hhmm);
                            index++;
                            isAttend = false;
                        } 
                        
                    }
                }
                
            }
            
            scheduleResData.add(schedule);
            
        }
        resData.put("schedule", scheduleResData);
        log.debug("====Attendance Detail Service Close====");
        return resData;
    }
    /**
     * 출결 확정 서비스
     * @param userCd
     * @param data
     * @param resData
     * @return
     */
    public Map<String, Object> attendanceProc(String userCd, Map<String, Object> data, Map<String, Object> resData, Map<String, String> windowData) throws Exception {
        log.debug("====Attendance Submit Open====");
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
        //재 출근일 경우(기존의 출결 데이터를 강제 삭제한다)
        /*
        String confirmCd = String.valueOf(data.get("confirm_cd"));
        if(!confirmCd.trim().isEmpty()) {
            int fourErrChk = Integer.parseInt(confirmCd);
            if(fourErrChk==4) {
                attendanceDao.reAttendingToDeleteStatus(dto);
            }
        }
        */
        //출결 데이터 등록
        Integer result = attendanceDao.registAttendance(dto);
        if(result == 0) {
            throw new Exception();
        }
        String typeMsg = "";
        String successMsg = "현재 시간 %t에 %s 하셨습니다.";
        int attType = dto.getAttendanceType();
        typeMsg = getStringType(attType, windowData);
        msgList.add(successMsg.replace("%s", typeMsg).replace("%t", nowTime));
        checkRegistSystem(data, "Attendance Proc", userCd);
        resData.put(RES, SUCCESS);
        resData.put(MSG, msgList);
        log.debug("====Attendance Submit Close====");
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
    
    private String getStringType(int type, Map<String, String> windowData) {
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
     * @param windowData 
     * @return
     */
    public Map<String, Object> getAttendanceData(String userCd, Map<String, Object> resData, Map<String, String> windowData) throws Exception {
        log.debug("====Attendance Window Open "  + "userCd :" + userCd + "====");
        Map<String, String> datas = new HashMap<String, String>();
        AttendanceDto dto = attendanceDao.getNewestAttendance(userCd);
        String attendanceSts = getMsg(windowData.get("ATTSTS1"),"출근 대기");
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
            attendanceSts = getMsg(windowData.get("ATTSTS2"),"출근 중");
            attRegTime = regTimeFormat.format(regDate);
            //두 시간사이가 48시간(이틀)가 지났을 경우
            if(regNowDiffDays > 48) {
                //출근 대기로 변경(전날 못찍은건 에러처리)
                attendanceSts = getMsg(windowData.get("ATTSTS1"),"출근 대기");
                attRegTime = "";
              //두 시간사이가 24시간(하루)가 지났을 경우
            } else if(regNowDiffDays > 24) {
                alertMsg = getMsg(windowData.get("ATTERR1"),"출근한지 하루가 지났습니다! 퇴근을 찍어주세요");
            }
            break;
        case offWork :
          //퇴근 완료가 오늘 날짜라면 퇴근 완료, 아니면 출근 대기상태로 유지
            if(isEqual(sdf.format(regDate), sdf.format(nowDate))) {
                attendanceSts = getMsg(windowData.get("ATTSTS3"),"퇴근 완료");
                attRegTime = regTimeFormat.format(regDate);
            }
            break;
        case earlyDeparture :
          //조퇴 완료가 오늘 날짜라면 조퇴 완료, 아니면 출근 대기상태로 유지
            if(isEqual(sdf.format(regDate), sdf.format(nowDate))) {
                attendanceSts = getMsg(windowData.get("ATTSTS4"),"조퇴 완료");
                attRegTime = regTimeFormat.format(regDate);
            }
            break;
        case breakTime :
            attendanceSts = getMsg(windowData.get("ATTSTS5"),"출근 중(휴식)");
            attRegTime = regTimeFormat.format(regDate);
            if(regNowDiffDays > 48) {
                //출근 대기로 변경(전날 못찍은건 에러처리)
                attendanceSts = getMsg(windowData.get("ATTSTS1"),"출근 대기");
                attRegTime = "";
              //두 시간사이가 24시간(하루)가 지났을 경우
            } else if(regNowDiffDays > 24) {
                alertMsg = getMsg(windowData.get("ATTERR2"),"휴식한지 하루가 지났습니다! 휴식 완료를 찍어주세요");
            }
            //상태가 휴식완료(1)일 경우
            if(isEqual(dto.getAttendanceStatus(), 1)) {
                attendanceSts = getMsg(windowData.get("ATTSTS6"), "출근 중(휴식 완료)");
                //가장 최근의 출근 데이터 취득 후 출근한지 얼마나 되었는지 확인
                AttendanceDto attendData = attendanceDao.getNewestAttendData(userCd);
                Date attendDate = attendData.getAttendanceRegDate();
                long attNowDiff = (nowDate.getTime() - attendDate.getTime()) / 1000; //밀리초
                long attNowDiffDays = attNowDiff / (60*60); //초, 분
                attRegTime = regTimeFormat.format(attendDate);
                if(regNowDiffDays > 48) {
                    //출근 대기로 변경(전날 못찍은건 에러처리)
                    attendanceSts = getMsg(windowData.get("ATTSTS1"),"출근 대기");
                    attRegTime = "";
                  //두 시간사이가 24시간(하루)가 지났을 경우
                } else if(attNowDiffDays > 24) {
                    alertMsg = getMsg(windowData.get("ATTERR1"),"출근한지 하루가 지났습니다! 퇴근을 찍어주세요");
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
     * 출결 체크 서비스
     * @param userCd
     * @param data
     * @param resData
     * @param windowData
     * @return
     */
    public Map<String, Object> attendanceCheck(String userCd, 
            Map<String, Object> data,
            Map<String, Object> resData,
            Map<String, String> windowData) throws Exception {
        log.debug("====Attendance Check Open====");
        List<String> msgList = new ArrayList<String>();
        int errCd = 0;
        //가장 최근(24시간 전까지의)의 출결데이터를 취득한다.
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
                        String typeMsg = getStringType(newType, windowData);
                        msgList.add("이미 %s 완료중입니다. 재출근을 하시겠습니까?".replace("%s", typeMsg));
                    } else {
                        if(newType==breakTime) {
                            errCd = 8;
                            String typeMsg = getStringType(newType, windowData);
                            msgList.add("%s 처리가 되어 있으므로 퇴근을 하셔야 재 출근이 가능합니다.".replace("%s", typeMsg));
                        }
                    }
                    break;
                case offWork :
                case earlyDeparture :
                    if(!(newType==goToWork || 
                        (newType==breakTime && newData.getAttendanceStatus()==1))) {
                        errCd = 6;
                        String typeMsg = getStringType(attType, windowData);
                        msgList.add("출근 중이 아니기 때문에 %s 하실 수 없습니다.".replace("%s", typeMsg));
                    }
                    break;
                case breakTime :
                    if(!(newType==goToWork || 
                            newType==breakTime)) {
                        errCd = 7;
                        String typeMsg = getStringType(attType, windowData);
                        String typeMsg2 = getStringType(newType, windowData);
                        msgList.add("%s1 하셨으므로 %s2 이 불가능합니다.".replace("%s1", typeMsg2)
                                .replace("%s2", typeMsg));
                    }
                    break;
                }
            }
        }
        if(errCd != 0) {
            data.put("confirm_cd", errCd);
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
        log.debug("====Attendance Check Close====");
        return resData;
    }

}
