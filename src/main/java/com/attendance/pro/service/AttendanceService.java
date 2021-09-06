package com.attendance.pro.service;

import static com.attendance.pro.other.CodeMap.ERROR;
import static com.attendance.pro.other.CodeMap.MSG;
import static com.attendance.pro.other.CodeMap.RES;
import static com.attendance.pro.other.CodeMap.SUCCESS;
import static com.attendance.pro.other.CodeMap.isEqual;

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

    /**
     * 유저 출결 서비스
     * @param userCd
     * @param data
     * @param resData
     * @return
     */
    public Map<String, Object> attendanceProc(String userCd, Map<String, Object> data, Map<String, Object> resData) {
        log.info("====Attendance Process Start====");
        List<String> msgList = new ArrayList<String>();
        //오늘 날짜의 유저 seq를 불러온다
        log.info(userCd);
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
        //출결 데이터 등록
        Integer result = 0;
        try {
            result = attendanceDao.registAttendance(dto);
        } catch(Exception e) {
            log.error(e.getMessage());
            msgList.add("데이터가 정상적으로 등록되지 않았습니다.");
            resData.put(RES, ERROR);
            resData.put(MSG, msgList);
            return resData;
        }
        if(result == 0) {
            msgList.add("데이터가 정상적으로 등록되지 않았습니다.");
            resData.put(RES, ERROR);
            resData.put(MSG, msgList);
            return resData;
        }
        String typeMsg = "";
        String successMsg = "현재 시간 %t에 %s 하셨습니다.";
        int attType = dto.getAttendanceType();
        switch(attType) {
        case goToWork :
            typeMsg = "출근";
            break;
        case offWork :
            typeMsg = "퇴근";
            break;
        case earlyDeparture :
            typeMsg = "조퇴";
            break;
        case breakTime :
            typeMsg = "휴식";
            break;
        }
        msgList.add(successMsg.replace("%s", typeMsg).replace("%t", nowTime));
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

    /**
     * 출결 화면 기동시
     * @param userCd
     * @param resData
     * @return
     */
    public Map<String, Object> getAttendanceData(String userCd, Map<String, Object> resData) {
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
        if(isEqual(dto.getAttendanceType(),goToWork)) {
            attendanceSts = "출근 중";
            attRegTime = regTimeFormat.format(regDate);
            //두 시간사이가 24시간(하루)가 지났을 경우
            if(regNowDiffDays > 24) {
                alertMsg = "출근한지 하루가 지났습니다! 퇴근을 찍어주세요";
            }
        } else if(isEqual(dto.getAttendanceType(),offWork)) {
            //퇴근 완료가 오늘 날짜라면 퇴근 완료, 아니면 출근 대기상태로 유지
            if(isEqual(sdf.format(regDate), sdf.format(nowDate))) {
                attendanceSts = "퇴근 완료";
                attRegTime = regTimeFormat.format(regDate);
            }
        } else if(isEqual(dto.getAttendanceType(), earlyDeparture)) {
            //조퇴 완료가 오늘 날짜라면 조퇴 완료, 아니면 출근 대기상태로 유지
            if(isEqual(sdf.format(regDate), sdf.format(nowDate))) {
                attendanceSts = "조퇴 완료";
                attRegTime = regTimeFormat.format(regDate);
            }
        } else if(isEqual(dto.getAttendanceType(), breakTime)) {
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
        }
        datas.put("att_time", attRegTime);
        datas.put("att_sts", attendanceSts);
        datas.put("att_msg", alertMsg);
        resData.put("datas", datas);
        log.info("====Attendance Window End====");
        return resData;
    }

}
