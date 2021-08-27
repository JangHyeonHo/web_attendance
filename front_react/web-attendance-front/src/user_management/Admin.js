import React from 'react';
//form의 validation을 용이하게 체크하게 해줌
import * as Yup from 'yup';
import WindowId from '../WindowId';
import axios from 'axios';
import { AppBar, Tabs, Tab } from "@material-ui/core"; 
import { AddCircle,ContactPhone } from "@material-ui/icons";
import { DataGrid } from "@mui/x-data-grid";


export default function Admin(){

    const valueTest = [{num:1,id:"kwon"}]
    const rows =[
                    {num:1,id:"kwon"},{num:2,id:"kwon2"}
                ]
    const columns =[
                    {field:"num",HeaderName:"번호",width:200},
                    {field:"id",HeaderName:"아이디",width:200}
                ]

    return (
    <div>
       admin(각 회사-혹은 부서 별 관리자의 역할이 될 것)계정으로 접속하셨습니다. <br/>
       todo 여기에 넣을 것 =&gt; 1. 멤버 관리 시스템(멤버 리스트), 2. 멤버 등록 시스템(멤버 레지스트) <br/>
       일단은 요정도 이후 로그인에 회사(부서) 시스템 추가 및 DB변경 회사(부서)코드, 회사명, 직급, 등등
       <AppBar position="static">
           <Tabs aria-label="Main Tabs">
                <Tab label="번호" icon={<AddCircle />}></Tab>
                <Tab label="아이디" icon={<ContactPhone />}></Tab>
           </Tabs>            
       </AppBar>
        <div style={{height:500 , width:800}}>
             {/* rowPerPageOpt = 한번에 이동하는 페이지 개수
                 pageSize = 한페이지당 보여지는 개수 */ }
            <DataGrid rows={rows} columns={columns} rowsPerPageOptions={[1]} pageSize={1}/>      
        </div>
       

    </div>);

}