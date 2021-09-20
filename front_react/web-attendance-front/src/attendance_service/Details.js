import React, { useEffect, useState } from "react";
import { Paper, TableContainer, Table, TableHead, TableRow, TableCell, TableBody} from "@material-ui/core";
import { makeStyles, withStyles } from "@material-ui/core/styles";
import CircularProgress from '@material-ui/core/CircularProgress';
import IsNotLang from '../common_service/IsNotLang';
import WindowId from "../WindowId";
import axios from "axios";

export default function Details({windows, datas, getActions}){
    const StyledTableCell = withStyles((theme) => ({
        head: {
          backgroundColor: theme.palette.warning.light,
          color: theme.palette.common.white,
        },
        body: {
          fontSize: 14,
        },
      }))(TableCell);
      
      const StyledTableRow = withStyles((theme) => ({
        root: {
          '&:nth-of-type(odd)': {
            backgroundColor: theme.palette.action.hover,
          },
        },
      }))(TableRow);
      
      function createData(days, fixIn, fixOut, staIn, staOut, remark) {
        return { days, fixIn, fixOut, staIn, staOut, remark };
      }

      const dateArray = ["일", "월", "화", "수", "목", "금", "토"];
      const monthArray = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
      const nowYear = new Date().getFullYear();
      const nowMonth = new Date().getMonth();

      const [rows,setRows] = useState([]);
      const [selectMonth, setSelectMonth] = useState(nowMonth);
      const [selectYear, setSelectYear] = useState(nowYear);
      const [inLoading,setInLoading] = useState(false);

      async function attDetailProc(values){
        const headers={
            'Content-type': 'application/json'
        };
        const resData = await axios.post('/api', values, {headers});
        return resData;
      }
    
      const data = { months : '', years : '', 
                        action : '',
                        win_id : WindowId("attdetails")};
               
      
      useEffect(()=>{
        setInLoading(false);
        data.months = parseInt(selectMonth);
        data.years = selectYear;
        data.action = getActions("details");
        attDetailProc(data).then((res)=>{
          if(res.data.window!==WindowId("attdetails")){
            window.location.replace('/login');
          }
          console.log(res.data);
          const schedule = res.data.schedule;
          var rowsData = [];
          var days = monthArray[selectMonth];
          if(selectMonth==1){
            if(selectYear%4==0){
              //윤달일 경우 29일로 처리
              days = days+1;
            }
          }
          var month = parseInt(selectMonth)+1;
          for(var i = 1; i < days + 1; i++){
            var daysMsg = mapArrayToDate(i, new Date(selectYear+"-"+month+"-"+i).getDay());
            /*
            if(schedule[i-1].isHoliday){

            }
            */
            var fixIn = "";
            var fixOut = "";
            var staIn = "";
            var staOut = "";
            if(schedule[i-1].fixScheduleIn!==null && schedule[i-1].fixScheduleIn!==""){
              fixIn = schedule[i-1].fixScheduleIn.substr(0,2)+":"+schedule[i-1].fixScheduleIn.substr(2,2)
            }
            if(schedule[i-1].fixScheduleOut!==null && schedule[i-1].fixScheduleOut!==""){
              fixOut = schedule[i-1].fixScheduleOut.substr(0,2)+":"+schedule[i-1].fixScheduleOut.substr(2,2)
            }
            if(schedule[i-1].stampIn!==null && schedule[i-1].stampIn!==""){
              staIn = schedule[i-1].stampIn.substr(0,2)+":"+schedule[i-1].stampIn.substr(2,2)
            }
            if(schedule[i-1].stampOut!==null && schedule[i-1].stampOut!==""){
              staOut = schedule[i-1].stampOut.substr(0,2)+":"+schedule[i-1].stampOut.substr(2,2)
            }
            rowsData.push(createData(daysMsg, fixIn, fixOut, staIn, staOut, schedule[i-1].remark));
          }
          setRows(rowsData);
          setInLoading(true);
        });
      },[selectMonth, selectYear]);

      const mapColorToDate = (index)=>{
          let className = "cal-";
          if (index == 0) {
            return className + "date-sun"
          } else if (index == 6) {
            return className + "date-sat"
          } else {
            return className + "date-weekday"
          }
      }

      function getYears(){
        var rowsData = [];
        for(var i = -3; i < 2; i++){
          rowsData.push(<option key={nowYear+i} value={nowYear+i}>{nowYear+i}</option>)
        }
        return rowsData;
      }

      const mapArrayToDate = (days, index) => {
          return (
            <span className={mapColorToDate(index)}>
              {days}({dateArray[index]})
            </span>
          )
      }
      /*[
        createData('1(수)', "9:00", "18:00", "8:30", "18:07", "비고"),
        createData('2(목)', "9:00", "18:00", "8:50", "18:10", "비고"),
        createData('3(금)', "9:00", "18:00", "8:12", "18:10", "비고"),
        createData('4(토)', "9:00", "18:00", "9:00", "18:30", "비고"),
        createData('5(일)', "9:00", "18:00", "8:34", "19:50", "비고"),
      ];*/
      
      const useStyles = makeStyles({
        table: {
          minWidth: 700,
        },
      });

      const classes = useStyles();
    return(
        <div className = "container-type-lg mt-5">
            <h2 className = "text-center my-3 orange">출결 조회</h2>
            <div>
                <TableContainer className="x-scroll-none" component={Paper}>
                  <div className="row my-1 justify-content-end">
                    <div className="col-2">
                      <select value={selectYear}
                        className ="form-select"
                        onChange={(value)=>{
                          setSelectYear(value.target.value);
                        }}> 
                      {getYears()}
                      </select>
                    </div>
                    <div className="col-1 align-self-center">년</div>
                    <div className="col-2">
                      <select value={selectMonth}
                        className ="form-select"
                        onChange={(value)=>{
                          setSelectMonth(value.target.value);
                        }}> 
                      {monthArray.map((row, index)=>(
                        <option key={index} value={index}>{index+1}</option>
                      ))}
                      </select>
                    </div>
                    <div className="col-1 align-self-center mr-1">월</div>
                  </div>
                    <Table className={classes.table} aria-label="customized table">
                        <TableHead>
                            <TableRow>
                                <StyledTableCell rowSpan={2} align="center">{IsNotLang(windows.DATES, "날짜")}</StyledTableCell>
                                <StyledTableCell colSpan={2} align="center">{IsNotLang(windows.USERSCHE, "유저 스케쥴")}</StyledTableCell>
                                <StyledTableCell colSpan={2} align="center">{IsNotLang(windows.INPUTTIME, "입력시간")}</StyledTableCell>
                                <StyledTableCell rowSpan={2} align="center">{IsNotLang(windows.REMARK, "비고")}</StyledTableCell>
                            </TableRow>
                            <TableRow>
                                <StyledTableCell align="center">출근 시간</StyledTableCell>
                                <StyledTableCell align="center">퇴근 시간</StyledTableCell>
                                <StyledTableCell align="center">출근 시간</StyledTableCell>
                                <StyledTableCell align="center">퇴근 시간</StyledTableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                        {inLoading ? rows.map((row, index) => (
                            <StyledTableRow key={index}>
                            <StyledTableCell align="center" component="th" scope="row">
                                {row.days}
                            </StyledTableCell>
                            <StyledTableCell align="center">{row.fixIn}</StyledTableCell>
                            <StyledTableCell align="center">{row.fixOut}</StyledTableCell>
                            <StyledTableCell align="center">{row.staIn}</StyledTableCell>
                            <StyledTableCell align="center">{row.staOut}</StyledTableCell>
                            <StyledTableCell align="left">{row.remark}</StyledTableCell>
                            </StyledTableRow>
                        ))
                        :
                          <StyledTableRow key={''}>
                            <StyledTableCell colSpan={6} align="center"><CircularProgress /></StyledTableCell>
                          </StyledTableRow>
                        }
                        </TableBody>
                    </Table>

                </TableContainer>

            </div>
        </div>
    );
}