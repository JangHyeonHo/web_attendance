import React from 'react';
//form의 validation을 용이하게 체크하게 해줌
import { Formik, Form, Field, ErrorMessage } from 'formik';
import * as Yup from 'yup';
import WindowId from '../WindowId';
import axios from 'axios';
import IsNotLang from '../common_service/IsNotLang';
import Router, { Route } from 'react-router-dom'

export default function Login({windows={}}){
    const initData = { user_email : "", user_pwd : "", win_id : WindowId("login")};
    
    async function submitLoginProc(values){
        const headers={
            'Content-type': 'application/json'
        };
        const resData = await axios.post('/api', values, {headers});
        return resData;
    }

    //validation check
    const validationSchema = Yup.object().shape({
        user_email : Yup.string()
            .email(IsNotLang(windows.EMAILERR1,"이메일 형식이 아닙니다."))
            .required(IsNotLang(windows.EMAILERR2,"이메일을 입력해 주세요.")),
        user_pwd : Yup.string()
            .required(IsNotLang(windows.PWDERR1,"비밀번호를 입력해 주세요."))
            .matches(/^((?=.*[\d])(?=.*[a-z])(?=.*[A-Z])|(?=.*[a-z])(?=.*[A-Z])(?=.*[^\w\d\s])|(?=.*[\d])(?=.*[A-Z])(?=.*[^\w\d\s])|(?=.*[\d])(?=.*[a-z])(?=.*[^\w\d\s])).{8,30}$/
            ,IsNotLang(windows.PWDERR2,"비밀번호는 영문자, 특수문자, 숫자를 포함하여 \n8자 이상, 30글자 미만으로 해주세요"))
    });

    return(
        <Formik 
            initialValues = {initData}
            validationSchema = {validationSchema}
            onSubmit = {async (values, {setErrors}) =>{
                //입력값 확인용
                console.log(values);
                submitLoginProc(values).then((resp)=>{
                    const retData = resp.data;
                    if(retData.res==="S"){
                        window.location.replace('/index');
                    } else{
                        setErrors({user_email:retData.msg,user_pwd:retData.msg});
                    }
                });
                
                
            }}
        >
            {(formik) => {
                const {
                    errors,
                    touched,
                    isValid,
                    dirty
                } = formik;
            return (
                <div className="form-type-sm mt-5">
                    <Form className="form-control">
                        <h2 className = "text-center mt-3">{IsNotLang(windows.LOGIN,"로그인")}</h2>
                        <div className = "form-floating my-3">
                            <Field type = "text" 
                                id = "user_email" 
                                name = "user_email" 
                                //errors -> 해당 Field에 에러가 있는지
                                //touched -> 해당 Field(input을 한번이라도 만졌는지)
                                className={errors.user_email && touched.user_email 
                                    ? "form-control is-invalid" 
                                    : touched.user_email
                                        ? "form-control is-valid"
                                        : "form-control"
                                    } 
                                required
                                autoComplete="off"/>
                            <ErrorMessage name="user_email" component ="div" className="invalid-feedback" />
                            <label htmlFor="user_id">{IsNotLang(windows.EMAIL,"이메일")}</label>
                        </div>
                        <div className = "form-floating mb-4">
                            <Field type = "password" 
                                id = "user_pwd" 
                                name = "user_pwd" 
                                className={errors.user_pwd && touched.user_pwd 
                                    ? "form-control is-invalid" 
                                    : touched.user_pwd
                                        ? "form-control is-valid"
                                        : "form-control"
                                    } 
                                required/>
                            <ErrorMessage name="user_pwd" component ="div" className="invalid-feedback pre-wrap" />
                            <label htmlFor="user_pwd">{IsNotLang(windows.PWD,"비밀번호")}</label>
                        </div>
                        <div className = "mb-2">
                            <button type = "submit" 
                                className= "btn back-orange col-12"
                                disabled = {!(dirty && isValid)}>
                                    {IsNotLang(windows.LOGIN,"로그인")}
                            </button>
                        </div>
                        <div id = "pwdSearch" 
                                role = "button"
                                className="text-end my-1 black"
                                onClick={()=>{
                                    Location.href="./?winId=pwdSearch"
                                }}>
                                    {IsNotLang(windows.PWDSEARCH,"비밀번호 찾기")}
                        </div>
                    </Form>
                    {/**테스트용 */}
                    <button className= "btn orange col-6" id = "admin" onClick={()=>{
                        const values = { user_email : "admin@webatt.com", user_pwd : "adminadmin1!", win_id : "W001"};
                        submitLoginProc(values).then((resp)=>{
                            const retData = resp.data;
                            window.location.replace('/admin');
                        });
                    }}>test용 관리자 로그인</button>
                </div>
            );
        }}
            
        </Formik>
    );

}