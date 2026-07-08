package com.attendance.pro.service;

import static com.attendance.pro.common.CodeMap.ERROR;
import static com.attendance.pro.common.CodeMap.MSG;
import static com.attendance.pro.common.CodeMap.RES;
import static com.attendance.pro.common.CodeMap.RESULT;
import static com.attendance.pro.common.CodeMap.SUCCESS;
import static com.attendance.pro.common.CodeMap.getMsg;
import static com.attendance.pro.common.CodeMap.isAnyEqual;
import static com.attendance.pro.common.CodeMap.isEqual;

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
import org.springframework.stereotype.Service;

import com.attendance.pro.dao.AttendanceDao;
import com.attendance.pro.dao.LogicServiceDao;
import com.attendance.pro.dao.ScheduleManagementDao;
import com.attendance.pro.dto.AttendanceDto;
import com.attendance.pro.dto.HolidayValuesDto;
import com.attendance.pro.dto.ScheduleManagementDto;
import com.attendance.pro.dto.UserDto;
import com.attendance.pro.response.AttendanceDetailResData;

/**
 * 출결 처리 서비스.
 * 출결 타입: 1=출근, 2=퇴근, 3=조퇴, 4=휴식
 */
@Service
public class AttendanceService extends BaseService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    /** 출근 */
    private static final int GO_TO_WORK = 1;
    /** 퇴근 */
    private static final int OFF_WORK = 2;
    /** 조퇴 */
    private static final int EARLY_DEPARTURE = 3;
    /** 휴식 */
    private static final int BREAK_TIME = 4;

    private final AttendanceDao attendanceDao;
    private final ScheduleManagementDao scheduleManagementDao;

    public AttendanceService(LogicServiceDao logicServiceDao,
            AttendanceDao attendanceDao,
            ScheduleManagementDao scheduleManagementDao) {
        super(logicServiceDao);
        this.attendanceDao = attendanceDao;
        this.scheduleManagementDao = scheduleManagementDao;
    }

    /**
     * 출결 처리 서비스.
     * action: 1=체크로직, 2=확정로직, 3=상세 데이터 취득
     *
     * @param data       요청 받은 데이터
     * @param resData    반환할 데이터
     * @param windowData 화면 값(언어설정)
     * @param props      [0]=유저 코드
     */
    @Override
    public Map<String, Object> proc(Map<String, Object> data, Map<String, Object> resData, Map<String, String> windowData, String... props) {
        log.debug("====Attendance Proc Open====");
        String userCd = props[0];
        log.debug("userCd : {}", userCd);
        List<String> msgList = new ArrayList<>();
        String action = String.valueOf(data.get("action"));
        try {
            if (isEqual(action, "1")) {
                //체크로직
                resData = attendanceCheck(userCd, data, resData, windowData);
            } else if (isEqual(action, "2")) {
                //확정로직 - 체크 시점의 데이터와 비교하기 위해 체크용 액션으로 되돌려서 해시를 비교한다.
                data.put("action", 1);
                String result = objectToString(data.get(RESULT));
                //데이터 변조가 없는지 확인(체크시와 확정시가 일치한지)
                if (isResultDatasEqual(result, data, userCd)) {
                    data.put("action", 2);
                    resData = attendanceProc(userCd, data, resData, windowData);
                } else {
                    //데이터 변조가 되어있으면 에러 처리
                    msgList.add("데이터가 올바르지 않습니다. 다시 처리해 주세요.");
                    resData.put(RES, ERROR);
                    resData.put(MSG, msgList);
                }
            } else if (isEqual(action, "3")) {
                //데이터 불러오기 로직
                resData = getAttendanceDetailDatas(userCd, data, resData, windowData);
            }
            log.debug("====Attendance Proc Close====");
            return resData;
        } catch (Exception e) {
            msgList.add("출결이 처리되지 않았습니다. 다시 시도해 주세요.");
            resData.put(RES, ERROR);
            resData.put(MSG, msgList);
            log.error("====Attendance Proc Error====", e);
            if (e instanceof SQLException sqlException) {
                errorRegistSystem(data, "Attendance", userCd, String.valueOf(sqlException.getErrorCode()), sqlException.getMessage());
            } else {
                StringBuilder errMsg = new StringBuilder();
                for (StackTraceElement st : e.getStackTrace()) {
                    errMsg.append(st.toString()).append('\n');
                }
                errorRegistSystem(data, "Attendance", userCd, "APERR", errMsg.toString());
            }
            return resData;
        }
    }

    /**
     * 출결 상세(월별) 데이터 취득 서비스.
     * 해당 월의 스케쥴과 출결 스탬프를 대조하여 일자별 출근/퇴근 시각을 만든다.
     */
    private Map<String, Object> getAttendanceDetailDatas(String userCd, Map<String, Object> data,
            Map<String, Object> resData, Map<String, String> windowData) {
        log.debug("====Attendance Detail Service Open====");
        //상세화면에서 요청한 날짜를 취득한다.
        Integer years = objectToInteger(data.get("years"));
        Integer months = objectToInteger(data.get("months"));
        //해당 날짜에 존재하는 스케쥴 값을 가져온다.
        Calendar selectedDate = Calendar.getInstance();
        //이번달
        selectedDate.set(years, months, 1, 0, 0, 0);
        Date nowDate = selectedDate.getTime();
        //다음달(이번달이 12월이라면 내년 1월 1일을 표시)
        if (months != 12) {
            selectedDate.set(years, months + 1, 1);
        } else {
            selectedDate.set(years + 1, 1, 1);
        }
        Date nextDate = selectedDate.getTime();
        //다음달+2일까지(최대 2일까지)(이번달이 12월이라면 내년 1월 2일을 표시)
        if (months != 12) {
            selectedDate.set(years, months + 1, 2);
        } else {
            selectedDate.set(years + 1, 1, 2);
        }
        Date stampNextDate = selectedDate.getTime();
        List<ScheduleManagementDto> getSchedule = scheduleManagementDao.getNowMonthSchedule(userCd, nowDate, nextDate);
        //출결 상황 취득
        List<AttendanceDto> getStamp = attendanceDao.getNowMonthStamp(userCd, nowDate, stampNextDate);
        List<AttendanceDetailResData> scheduleResData = new ArrayList<>();
        boolean isAttend = false;
        boolean isEnd = false;
        int index = 0;
        //스케쥴별로 출결 스탬프를 대조하여 응답 데이터를 만든다.
        for (ScheduleManagementDto sche : getSchedule) {
            AttendanceDetailResData schedule = new AttendanceDetailResData();
            HolidayValuesDto holidayChk = sche.getHolidayValuesDto();
            if (holidayChk != null && holidayChk.getHolidaySeq() > 0) {
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
            if (isEnd) {
                schedule.setStampIn("");
                schedule.setStampOut("");
                schedule.setHoliday(true);
                scheduleResData.add(schedule);
                continue;
            }
            //출결상황이 존재하지 않을때.
            if (getStamp.isEmpty()) {
                schedule.setStampIn("");
                schedule.setStampOut("");
                scheduleResData.add(schedule);
                continue;
            }
            stamp:
            for (int i = index; i < getStamp.size(); i++) {
                AttendanceDto stamp = getStamp.get(i);
                Integer attendType = stamp.getAttendanceType();
                //입력 시간이 스케쥴 날짜보다 이전일 경우는 필요없으니 다음으로 넘김
                if (stamp.getAttendanceDate().compareTo(sche.getScheduleDate()) < 0) {
                    continue;
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                SimpleDateFormat timeSdf = new SimpleDateFormat("HHmm");
                String scheFormat = sdf.format(sche.getScheduleDate());
                String stampFormat = sdf.format(stamp.getAttendanceDate());
                long diffTime = ((stamp.getAttendanceDate().getTime() - sche.getScheduleDate().getTime()) / 1000) / (60 * 60);
                /*
                 * 같은 날에 발생할 수 있는 패턴:
                 * 출근 출근 퇴근 / 출근 퇴근 퇴근 / 출근 => 다음 날 퇴근
                 */
                if (isEqual(scheFormat, stampFormat)) {
                    //출근 상태 여부랑 상관없이 출근이 온다면
                    if (isEqual(attendType, GO_TO_WORK)) {
                        isAttend = true; //출근 상태로 변환
                        schedule.setStampIn(timeSdf.format(stamp.getAttendanceDate())); //출근 시각 등록
                        index++;
                        //마지막 출근 데이터라면
                        if (i == getStamp.size() - 1) {
                            schedule.setStampOut("");
                            isEnd = true;
                            break stamp;
                        }
                    } else if (isAttend && isAnyEqual(attendType, OFF_WORK, EARLY_DEPARTURE)) {
                        //출근 상태이고 퇴근이라면
                        isAttend = false; //퇴근 상태로 변환
                        schedule.setStampOut(timeSdf.format(stamp.getAttendanceDate())); //퇴근 시각 등록
                        index++;
                    } else if (!isAttend && isAnyEqual(attendType, OFF_WORK, EARLY_DEPARTURE)) {
                        //출근 상태가 아닌데 퇴근일 경우(퇴근이 이미 찍힌 후라면)
                        schedule.setStampOut(timeSdf.format(stamp.getAttendanceDate()));
                        index++;
                    }
                } else {
                    //날짜가 같지 않을경우(즉 다음날일 경우)
                    if (!isAttend) {
                        //출근 상태가 아니라면
                        if (schedule.getStampIn() == null) {
                            schedule.setStampIn("");
                            if (schedule.getStampOut() == null) {
                                schedule.setStampOut("");
                            }
                            break stamp;
                        }
                        //퇴근이 또 찍혀있다면?
                        if (isAnyEqual(attendType, OFF_WORK, EARLY_DEPARTURE)) {
                            //퇴근 또 찍힌게 48시간 이후라면?
                            if (diffTime > 48) {
                                if (schedule.getStampOut() == null) {
                                    schedule.setStampOut("");
                                }
                                break stamp;
                            }
                            //다음날까지 일을 한 경우(24시간을 더하여 계산)
                            String hhmm = String.valueOf(
                                    Integer.parseInt(timeSdf.format(stamp.getAttendanceDate())) + 2400);
                            schedule.setStampOut(hhmm);
                            index++;
                        } else {
                            //출근일 경우
                            break stamp;
                        }
                    } else if (isEqual(attendType, GO_TO_WORK)) {
                        //다음날인데 출근이 연달아 찍혀있을 경우
                        //퇴근이 없는 걸로 간주하고 퇴근이라고 인식하게 한후 다음날 처리로 넘어감
                        schedule.setStampOut("");
                        isAttend = false;
                        break stamp;
                    } else if (isAnyEqual(attendType, OFF_WORK, EARLY_DEPARTURE)) {
                        //퇴근 또 찍힌게 48시간 이후라면?
                        if (diffTime > 48) {
                            if (schedule.getStampOut() == null) {
                                schedule.setStampOut("");
                            }
                            isAttend = false;
                            break stamp;
                        }
                        //다음날까지 일을 한 경우(24시간을 더하여 계산)
                        String hhmm = String.valueOf(
                                Integer.parseInt(timeSdf.format(stamp.getAttendanceDate())) + 2400);
                        schedule.setStampOut(hhmm);
                        index++;
                        isAttend = false;
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
     * 출결 확정 서비스.
     * 체크로직을 통과한 요청을 실제 ATTENDANCE 테이블에 등록한다.
     */
    public Map<String, Object> attendanceProc(String userCd, Map<String, Object> data, Map<String, Object> resData, Map<String, String> windowData) throws Exception {
        log.debug("====Attendance Submit Open====");
        List<String> msgList = new ArrayList<>();
        //오늘 날짜의 유저 seq를 불러온다
        Integer todaysSeq = attendanceDao.getAttendanceSeq(userCd);
        todaysSeq = (todaysSeq == null || todaysSeq == 0) ? 1 : todaysSeq + 1;
        AttendanceDto dto = dataToDto(data);
        //등록 유저 정보 입력
        Date date = new Date();
        String nowTime = new SimpleDateFormat("HH:mm").format(date);
        dto.setAttendanceSeq(todaysSeq);
        dto.setAttendanceDate(date);
        dto.setUserDto(new UserDto());
        dto.getUserDto().setUserCd(userCd);
        //출결 데이터가 휴식이라면 지난 출결 데이터가 휴식중인지 확인
        if (dto.getAttendanceType() == BREAK_TIME) {
            AttendanceDto breakDto = attendanceDao.getNewestAttendance(userCd);
            //휴식중인 상태라면 휴식 끝을 넣어준다.
            if (breakDto.getAttendanceType() == BREAK_TIME && breakDto.getAttendanceStatus() == 0) {
                dto.setAttendanceStatus(1);
            }
        }
        //출결 데이터 등록
        Integer result = attendanceDao.registAttendance(dto);
        if (result == 0) {
            throw new Exception("출결 데이터 등록에 실패했습니다.");
        }
        String successMsg = "현재 시간 %t에 %s 하셨습니다.";
        String typeMsg = getStringType(dto.getAttendanceType(), windowData);
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
        return switch (type) {
            case OFF_WORK -> "퇴근";
            case EARLY_DEPARTURE -> "조퇴";
            case BREAK_TIME -> "휴식";
            default -> "출근";
        };
    }

    /**
     * 출결 화면 기동시 현재 출결 상태(출근 대기/출근 중/퇴근 완료 등)를 취득한다.
     */
    public Map<String, Object> getAttendanceData(String userCd, Map<String, Object> resData, Map<String, String> windowData) throws Exception {
        log.debug("====Attendance Window Open userCd : {}====", userCd);
        Map<String, String> datas = new HashMap<>();
        AttendanceDto dto = attendanceDao.getNewestAttendance(userCd);
        String attendanceSts = getMsg(windowData.get("ATTSTS1"), "출근 대기");
        String alertMsg = "";
        //최근 한달간의 출퇴근 데이터가 존재하지 않으면 출근 대기로 값을 보냄
        if (dto == null || dto.getAttendanceType() == null) {
            datas.put("att_sts", attendanceSts);
            resData.put("datas", datas);
            return resData;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MM,dd");
        SimpleDateFormat regTimeFormat = new SimpleDateFormat("MM/dd HH:mm:ss");
        //등록 시각과 현재 시각의 차이(시간 단위)
        Date regDate = dto.getAttendanceRegDate();
        Date nowDate = new Date();
        long regNowDiffHours = ((nowDate.getTime() - regDate.getTime()) / 1000) / (60 * 60);
        String attRegTime = "";
        int attType = dto.getAttendanceType();
        switch (attType) {
        case GO_TO_WORK:
            attendanceSts = getMsg(windowData.get("ATTSTS2"), "출근 중");
            attRegTime = regTimeFormat.format(regDate);
            if (regNowDiffHours > 48) {
                //48시간(이틀)이 지났으면 출근 대기로 변경(전날 못찍은건 에러처리)
                attendanceSts = getMsg(windowData.get("ATTSTS1"), "출근 대기");
                attRegTime = "";
            } else if (regNowDiffHours > 24) {
                //24시간(하루)이 지났으면 퇴근 알림 표시
                alertMsg = getMsg(windowData.get("ATTERR1"), "출근한지 하루가 지났습니다! 퇴근을 찍어주세요");
            }
            break;
        case OFF_WORK:
            //퇴근 완료가 오늘 날짜라면 퇴근 완료, 아니면 출근 대기상태로 유지
            if (isEqual(sdf.format(regDate), sdf.format(nowDate))) {
                attendanceSts = getMsg(windowData.get("ATTSTS3"), "퇴근 완료");
                attRegTime = regTimeFormat.format(regDate);
            }
            break;
        case EARLY_DEPARTURE:
            //조퇴 완료가 오늘 날짜라면 조퇴 완료, 아니면 출근 대기상태로 유지
            if (isEqual(sdf.format(regDate), sdf.format(nowDate))) {
                attendanceSts = getMsg(windowData.get("ATTSTS4"), "조퇴 완료");
                attRegTime = regTimeFormat.format(regDate);
            }
            break;
        case BREAK_TIME:
            attendanceSts = getMsg(windowData.get("ATTSTS5"), "출근 중(휴식)");
            attRegTime = regTimeFormat.format(regDate);
            if (regNowDiffHours > 48) {
                //48시간(이틀)이 지났으면 출근 대기로 변경(전날 못찍은건 에러처리)
                attendanceSts = getMsg(windowData.get("ATTSTS1"), "출근 대기");
                attRegTime = "";
            } else if (regNowDiffHours > 24) {
                alertMsg = getMsg(windowData.get("ATTERR2"), "휴식한지 하루가 지났습니다! 휴식 완료를 찍어주세요");
            }
            //상태가 휴식완료(1)일 경우
            if (isEqual(dto.getAttendanceStatus(), 1)) {
                attendanceSts = getMsg(windowData.get("ATTSTS6"), "출근 중(휴식 완료)");
                //가장 최근의 출근 데이터 취득 후 출근한지 얼마나 되었는지 확인
                AttendanceDto attendData = attendanceDao.getNewestAttendData(userCd);
                Date attendDate = attendData.getAttendanceRegDate();
                long attNowDiffHours = ((nowDate.getTime() - attendDate.getTime()) / 1000) / (60 * 60);
                attRegTime = regTimeFormat.format(attendDate);
                if (regNowDiffHours > 48) {
                    attendanceSts = getMsg(windowData.get("ATTSTS1"), "출근 대기");
                    attRegTime = "";
                } else if (attNowDiffHours > 24) {
                    alertMsg = getMsg(windowData.get("ATTERR1"), "출근한지 하루가 지났습니다! 퇴근을 찍어주세요");
                }
            }
            break;
        default:
            break;
        }

        datas.put("att_time", attRegTime);
        datas.put("att_sts", attendanceSts);
        datas.put("att_msg", alertMsg);
        resData.put("datas", datas);
        log.debug("====Attendance Window End====");
        return resData;
    }

    /**
     * 출결 체크 서비스.
     * 확정 전, 요청한 출결 타입이 현재 상태에서 가능한지 검사하고 확인 코드(err_cd)를 돌려준다.
     */
    public Map<String, Object> attendanceCheck(String userCd,
            Map<String, Object> data,
            Map<String, Object> resData,
            Map<String, String> windowData) throws Exception {
        log.debug("====Attendance Check Open====");
        List<String> msgList = new ArrayList<>();
        int errCd = 0;
        //가장 최근(48시간 전까지의)의 출결데이터를 취득한다.
        AttendanceDto newData = attendanceDao.getNewestAttendance(userCd);
        AttendanceDto reqData = dataToDto(data);
        int attType = reqData.getAttendanceType();
        if (newData == null) {
            //가장 최근의 데이터가 존재하지 않을 경우 출근만 허용한다
            if (attType != GO_TO_WORK) {
                errCd = 5;
                msgList.add("출근 전이므로 퇴근/조퇴/휴식을 할 수 없습니다.");
            }
        } else {
            //가장 최근의 데이터가 존재하는 경우
            int newType = newData.getAttendanceType();
            //가장 최근의 출결데이터와 요청한 데이터의 타입이 일치하다면(같은 출근이나 같은 퇴근반복)
            //휴식의 경우 상관없음
            if (isEqual(newData.getAttendanceType(), reqData.getAttendanceType())) {
                switch (attType) {
                case GO_TO_WORK:
                    errCd = 1;
                    msgList.add("이미 출근 중입니다. 출근을 덮어쓸까요?");
                    break;
                case OFF_WORK:
                    errCd = 2;
                    msgList.add("이미 퇴근 중입니다. 퇴근을 덮어쓸까요?");
                    break;
                case EARLY_DEPARTURE:
                    errCd = 3;
                    msgList.add("이미 조퇴하셨습니다. 조퇴를 덮어쓸까요?");
                    break;
                default:
                    break;
                }
            }
            /*
             * 타입 비교
             * 출근: 같은 날의 퇴근, 조퇴기록이 있을 시 재 출근 여부를 물어봐야함. 이전 기록이 휴식이면 출근 불가능
             * 퇴근, 조퇴: 이전 기록이 출근이어야함(휴식일 경우 휴식끝(1)이어야함)
             * 휴식: 이전 기록이 휴식이거나 출근이어야함
             */
            if (errCd == 0) {
                switch (attType) {
                case GO_TO_WORK:
                    SimpleDateFormat sdf = new SimpleDateFormat("MM,dd");
                    if ((newType == OFF_WORK || newType == EARLY_DEPARTURE) &&
                            isEqual(sdf.format(newData.getAttendanceRegDate()), sdf.format(new Date()))) {
                        errCd = 4;
                        String typeMsg = getStringType(newType, windowData);
                        msgList.add("이미 %s 완료중입니다. 재출근을 하시겠습니까?".replace("%s", typeMsg));
                    } else if (newType == BREAK_TIME) {
                        errCd = 8;
                        String typeMsg = getStringType(newType, windowData);
                        msgList.add("%s 처리가 되어 있으므로 퇴근을 하셔야 재 출근이 가능합니다.".replace("%s", typeMsg));
                    }
                    break;
                case OFF_WORK:
                case EARLY_DEPARTURE:
                    if (!(newType == GO_TO_WORK ||
                            (newType == BREAK_TIME && newData.getAttendanceStatus() == 1))) {
                        errCd = 6;
                        String typeMsg = getStringType(attType, windowData);
                        msgList.add("출근 중이 아니기 때문에 %s 하실 수 없습니다.".replace("%s", typeMsg));
                    }
                    break;
                case BREAK_TIME:
                    if (!(newType == GO_TO_WORK || newType == BREAK_TIME)) {
                        errCd = 7;
                        String typeMsg = getStringType(attType, windowData);
                        String typeMsg2 = getStringType(newType, windowData);
                        msgList.add("%s1 하셨으므로 %s2 이 불가능합니다.".replace("%s1", typeMsg2)
                                .replace("%s2", typeMsg));
                    }
                    break;
                default:
                    break;
                }
            }
        }
        if (errCd != 0) {
            data.put("confirm_cd", errCd);
        }
        String result = checkRegistSystem(data, "Attendance Check", userCd);
        resData.put(RESULT, result);
        if (errCd == 0) {
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
