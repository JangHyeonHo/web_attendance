import {React, useEffect, useRef, useState} from "react";
import WindowId from '../WindowId';
import { useSpring, animated, useTransition, config } from "react-spring";
import axios from 'axios';
import Geolocation from "@react-native-community/geolocation";
import Clock from "react-live-clock";
import IsNotLang from '../common_service/IsNotLang';
import Details from "./Details";

export default function Attendance({windows={}, datas={}}){
    const [registBox,setRegistBox] = useState(false);
    const [detailBox,setDetailBox] = useState(false);
    const [detailWindows,setDetailWindows] = useState('');

    //react 애니메이션 사용(사용법을 잘 모르니 테스트해보는거)
    const transitions = useTransition(registBox, { 
                            from :{ opacity : 0, height : '0%', transform : 'translateY(-30px)'}
                            , enter : { opacity : 1, height : '100%', transform : 'translateY(0px)'}
                            , leave : { opacity : 0, height : '0%', transform : 'translateY(-30px)'}
                            , config : config.slow
                        });
    const actions = { check : 1, regist : 2, details : 3 }

    function getActions(key){
        return actions[key];
    }

    const data = { attendance_type : "", latitude : "", 
                        longitude : "", place_info : "",
                        terminal : "", error_cd : "",
                        error_msg : "", action : "",
                        result : "", confirm_cd : "",
                        win_id : WindowId("attendance")};

    const [latitude,setLatitude] = useState('');
    const [longitude,setLongitude] = useState('');
    const [placeInfo,setPlaceInfo] = useState('');
    const [errorCd,setErrorCd] = useState('');
    const [errorMsg,setErrorMsg] = useState('');
    const [attendanceType,setAttendanceType] = useState('');

    //위도 & 경도 취득
    const geoLocation = (attendanceType) => {
        Geolocation.getCurrentPosition(async position=>{
            const latitude = JSON.stringify(position.coords.latitude);
            const longitude = JSON.stringify(position.coords.longitude);
            setAttendanceType(attendanceType);
            setLatitude(latitude);
            setLongitude(longitude);
            setPlaceInfo("테스트용 주소");
            setRegistBox(true);
            console.log("lat : "+latitude);
            console.log("long : "+longitude);
            return data;
        },
        error => {
            alert("위치 정보 취득에 실패하셨습니다.");
            console.log(error.code, error.message);
            setErrorCd(error.code);
            setErrorMsg(error.message);
        },
        {enableHighAccuracy:true,
        timeout:15000,
        maximumAge:10000})
    }
    
    async function attendanceProc(values){
        const headers={
            'Content-type': 'application/json'
        };
        const resData = await axios.post('/api', values, {headers});
        return resData;
    }

    function getMsg(){
        switch(attendanceType){
            case 1 : 
                return IsNotLang(windows.ATTEND, "출근");
            case 2 : 
                return IsNotLang(windows.OFFWORK, "퇴근");
            case 3 : 
                return IsNotLang(windows.EARLY, "조퇴");
            case 4 : 
                return IsNotLang(windows.BREAKTIME, "휴식");
        }
    }

    function getTimeFormat(){
        return "HH시 mm분 ss초";
    }

    function attService(action, result, errCd){
        data.attendance_type = attendanceType;
        data.latitude = latitude;
        data.longitude = longitude;
        data.place_info = placeInfo;
        data.action = getActions(action);
        data.result = result ? result : "";
        data.confirm_cd = errCd ? errCd : "";
        if(errorCd || errorMsg){
            data.error_cd = errorCd;
            data.error_msg = errorMsg;
        }
        return attendanceProc(data);
    }

    return(
        <div>
        <div className = "container-type-sm mt-5">
            <div className = "x-300 mx-auto mb-3 fs-2 fw-bold text-center">
                {new Date().getMonth()+1}월 {new Date().getDate()}일
            </div>
            <div className = "x-300 mx-auto mb-3">
                <div className="text-start">현재 유저님의 상태는 </div>
                <div className="text-center fs-1 fw-bold cornflowerblue">{datas.att_sts}</div>
                <div className="text-end">입니다</div>
            </div>
            {datas.att_time && 
                <div className = "x-300 mx-auto mb-3">등록 시각 : {datas.att_time}</div>
            }
            {datas.att_msg && 
                <div className = "text-center mx-auto mb-3 fw-bold red">{datas.att_msg}</div>
            }
            <div className = "btn btn-group mx-auto x-300 left-50 right-50 mb-3">
                <button className = "btn back-orange" onClick={()=>{
                    geoLocation(1);
                }}>
                    {IsNotLang(windows.ATTEND, "출근")}
                </button>
                <button className = "btn back-orange" onClick={()=>{
                    geoLocation(2);
                }}>
                    {IsNotLang(windows.OFFWORK, "퇴근")}
                </button>
                <button className = "btn back-orange" onClick={()=>{
                    geoLocation(3);
                }}>
                    {IsNotLang(windows.EARLY, "조퇴")}
                </button>
                <button className = "btn back-orange" onClick={()=>{
                    geoLocation(4);
                }}>
                    {IsNotLang(windows.BREAKTIME, "휴식")}
                </button>
            </div>
            {transitions((styles, checkKeys)=>(
                checkKeys && 
                    (<animated.div style={styles} className = "x-300 mx-auto mb-3">
                        <div className="text-center fw-bold mb-1 fs-3">{getMsg()}</div>
                        <div className="text-center fw-bold mb-1">현재 시간 <span className="fw-light"><Clock format={getTimeFormat()} ticking={true}/></span></div>
                        <div className="text-center fw-bold mb-1">위도 <span className="fw-light">{latitude}</span></div>
                        <div className="text-center fw-bold mb-1">경도 <span className="fw-light">{longitude}</span></div>
                        <div className="text-center fw-bold mb-3">현재 위치 미완성</div>
                        <div className="btn btn-group w-100">
                            <button  className = "btn back-orange w-50" onClick={()=>{
                                var msg = getMsg();
                                if(window.confirm(msg + " 등록하시겠습니까?")){
                                    attService("check").then((resp)=>{
                                        const checkResp = resp.data;
                                        if(checkResp.window!==data.win_id){
                                            alert("세션이 종료되었습니다. 다시 로그인을 해주세요")
                                            window.location.replace('/login');
                                        }
                                        if(checkResp.res==="S"){
                                            attService("regist", checkResp.result).then((resp)=>{
                                                if(checkResp.window!==data.win_id){
                                                    alert("세션이 종료되었습니다. 다시 로그인을 해주세요")
                                                    window.location.replace('/login');
                                                }
                                                const retData = resp.data;
                                                if(retData.res==="S"){
                                                    alert(retData.msg[0]);
                                                    window.location.replace('./');
                                                } else {
                                                    alert(retData.msg[0]);
                                                }                                             
                                                setRegistBox(false);
                                            })
                                        } else{
                                            console.log(checkResp)
                                            //에러코드에 따라 처리;
                                            switch(checkResp.err_cd){
                                                //case 1, 2, 3, 4 같은값 2번 입력처리
                                                case 1 :
                                                case 2 :
                                                case 3 :
                                                case 4 :
                                                    if(window.confirm(checkResp.msg[0])){
                                                        attService("regist", checkResp.result, checkResp.err_cd).then((resp)=>{
                                                            if(checkResp.window!==data.win_id){
                                                                alert("세션이 종료되었습니다. 다시 로그인을 해주세요")
                                                                window.location.replace('/login');
                                                            }
                                                            const retData = resp.data;
                                                            if(retData.res==="S"){
                                                                alert(retData.msg[0]);
                                                                window.location.replace('./');
                                                            } else {
                                                                alert(retData.msg[0]);
                                                            }                                             
                                                            setRegistBox(false);
                                                        })
                                                    }
                                                    break;
                                                default :
                                                    alert(checkResp.msg[0]);
                                                    setRegistBox(false);
                                                    break;
                                            }
                                        }
                                    });
                                }
                            }}> 등록
                            </button>
                            <button className = "btn back-orange w-50" onClick={()=>{
                                geoLocation(attendanceType);
                            }}>
                                재요청
                            </button>
                        </div>
                    </animated.div>
                    )
            ))}
            
            <div className = "x-300 mx-auto mb-3">
                <button className = "btn back-orange w-100" onClick={()=>{
                    var url = '?win_id='+WindowId("attdetails");
                    axios.get('/api'+url).then(res=>{
                        console.log(res);
                        setDetailWindows(res.data.windows);
                        setDetailBox(!detailBox);
                      });
                }}>
                    {IsNotLang(windows.ATTDETAILS, "출결 조회")}
                </button>
            </div>
        </div>
        <div>
            {detailBox && (
            <Details windows={detailWindows} getActions={getActions}></Details>
            )}
        </div>
        </div>
    );

}