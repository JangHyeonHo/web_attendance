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

  function windowChange(windowId){
      const headers = {
        'Content-type': 'application/json'
      }
      axios.put('/api',{"win_id" : windowId},{headers}).then(res=>{
        setData(res.data);
      });
  }

  useEffect(
    ()=>{
        windowChange(null);
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
          return <Index />;
    }
  }
  

  return (
    <Router>
      <div>
        <header>
          <button className = "btn orange" onClick = {()=>windowChange(WindowId("index"))}>
              홈
          </button>
          {!data.user_name ?
          <button className = "btn orange" onClick = {()=>windowChange(WindowId("login"))}>
              로그인
          </button>
          :
          <button className = "btn orange" onClick = {()=>windowChange(WindowId("logout"))}>
              로그아웃
          </button>
          }
          {/**테스트용 */}
          <button className = "btn orange" onClick = {()=>windowChange(WindowId("admin"))}>
              관리자
          </button>
          {!data.user_name ?
          <button className = "btn orange" onClick = {()=>windowChange(WindowId("signup"))}>
              회원가입
          </button>
          :
          <span>{data.user_name} 님</span>
          }
        </header>
        <div className="Contents">
          <Route path="/" component={componentCall} />
        </div>
      </div>
    </Router>
  );


}

export default App;
