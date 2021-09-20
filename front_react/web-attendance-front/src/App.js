import Login from './user_management/Login'
import Index from './user_management/Index'
import Admin from './user_management/Admin'
import Attendance from './attendance_service/Attendance'
import Details from './attendance_service/Details'
import 'bootstrap/dist/css/bootstrap.min.css';
import './App.css';
import React, {useState, useEffect, useRef} from 'react';
import {BrowserRouter as Router, Route} from 'react-router-dom';
import WindowId from './WindowId';
import axios from 'axios';

function App() {
  const [data, setData] = useState('');

  function windowChange(win_id){
    console.log(win_id);
    console.log(WindowId(win_id));
    var url = "";
      if(win_id !== ""){
        const encodeId = WindowId(win_id);
        if(encodeId !== undefined){
          url = '?win_id='+encodeId;
          //url += '&lang='+"EN"; //(영어로 보고 싶을때)
        }
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
      window.history.replaceState(null,null,"./")
      windowChange(win_id);
    }, []
  );


  function componentCall(){
    switch(data.window){
      case WindowId("index") :
          return <Index />;
      case WindowId("login") :
          return <Login windows={data.windows}/>;
      case WindowId("admin") :
          return <Admin />;
      case WindowId("attendance") :
          return <Attendance windows={data.windows} datas={data.datas}/>;
      //case WindowId("member") :

      default :
          return (<div></div>);
    }
  }

  function headerChange(){
    console.log(data);
    //document.getElementById('home').innerText();
  }
  

  return (
    <Router>
      <div>
        {data.headers ?
          <header className="container-type-md">
            <button id="home" className = "btn back-orange" onClick = {()=>windowChange('index')}>
              {data.headers.HOME}
            </button>
            {!data.user_name ?
            <button id="login" className = "btn back-orange" onClick = {()=>windowChange('login')}>
              {data.headers.LOGIN}
            </button>
            :
            <button id="logout" className = "btn back-orange" onClick = {()=>windowChange('logout')}>
              {data.headers.LOGOUT}
            </button>
            }
            {/**테스트용 */}
            <button id="admin" className = "btn back-orange" onClick = {()=>windowChange('admin')}>
              {data.headers.ADMIN}
            </button>
            {!data.user_name ?
            <button id="signup" className = "btn back-orange" onClick = {()=>windowChange('signup')}>
              {data.headers.SIGNUP}
            </button>
            :
            <span>{data.user_name} 님</span>
            }
            <button className= "btn back-orange" id = "admin" onClick={()=>{
                          window.location.replace("/admin");
                      }}>화면이동 테스트용
            </button>
          </header>
        : <header className="container-type-md"></header> }
        <div className="Contents">
          <Route path="/" component={componentCall} />
        </div>
      </div>
    </Router>
  );


}

export default App;
