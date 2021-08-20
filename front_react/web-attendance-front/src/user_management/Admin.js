import React from 'react';
//form의 validation을 용이하게 체크하게 해줌
import * as Yup from 'yup';
import WindowId from '../WindowId';
import axios from 'axios';

export default function Admin(){

    return (
    <div>
       admin(각 회사-혹은 부서 별 관리자의 역할이 될 것)계정으로 접속하셨습니다. <br/>
       todo 여기에 넣을 것 =&gt; 1. 멤버 관리 시스템(멤버 리스트), 2. 멤버 등록 시스템(멤버 레지스트) <br/>
       일단은 요정도 이후 로그인에 회사(부서) 시스템 추가 및 DB변경 회사(부서)코드, 회사명, 직급, 등등
       

    </div>);

}