import Login from './user_management/Login'
import Index from './user_management/Index'
import Admin from './user_management/Admin'
import 'bootstrap/dist/css/bootstrap.min.css';
import './App.css';
import React, {useState, useEffect} from 'react';
import {BrowserRouter as Router, Route} from 'react-router-dom';
import WindowId from './WindowId';
import axios from 'axios';

function App() {
  const [data, setData] = useState('');

  function windowChange(win_id){
    console.log(win_id);
    console.log(WindowId(win_id));
    var url = "";
      if(win_id!==""){
        url = '?win_id='+WindowId(win_id);
      }
      console.log(window.location)
      console.log(url);
      axios.get('/api'+url).then(res=>{
        console.log(res);
        setData(res.data);
        componentCall();
      });
  }

  useEffect(
    ()=>{
      const win_id = window.location.pathname.replace('/','');
      windowChange(win_id);
    }, []
  );


  function componentCall(){
    switch(data.window){
      case WindowId("index") :
          return <Index />;
      case WindowId("login") :
          return <Login />;
      case WindowId("admin") :
          return <Admin />;
      //case WindowId("member") :

      default :
          return <LoadingWindow/>;
    }
  }
  

  return (
    <Router>
      <div>
        <header>
          <button className = "btn orange" onClick = {()=>windowChange('index')}>
              홈
          </button>
          {!data.user_name ?
          <button className = "btn orange" onClick = {()=>windowChange('login')}>
              로그인
          </button>
          :
          <button className = "btn orange" onClick = {()=>windowChange('logout')}>
              로그아웃
          </button>
          }
          {/**테스트용 */}
          <button className = "btn orange" onClick = {()=>windowChange('admin')}>
              관리자
          </button>
          {!data.user_name ?
          <button className = "btn orange" onClick = {()=>windowChange('signup')}>
              회원가입
          </button>
          :
          <span>{data.user_name} 님</span>
          }
          <button className= "btn orange col-6" id = "admin" onClick={()=>{
                        window.location.replace("/admin");
                    }}>화면이동 테스트용
          </button>
        </header>
        <div className="Contents">
          <Route path="/" component={componentCall} />
        </div>
      </div>
    </Router>
  );


}

function LoadingWindow(){
  return (
    <div></div>
  );
}

export default App;
