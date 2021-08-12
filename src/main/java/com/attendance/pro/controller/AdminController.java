package com.attendance.pro.controller;

import static com.attendance.pro.other.Redirector.redirect;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import com.attendance.pro.other.AdminSettingLogic;


@Controller("/")
public class AdminController {
    
    @Autowired
    AdminSettingLogic adminSettingLogic;
    
    
    @RequestMapping(method = RequestMethod.GET)
    public String root() {
        return redirect("login");
    }
    
    //임시용 초기 설정화면 제어용 admin_settings
    @RequestMapping(value = "/admin_settings", method = RequestMethod.GET)
    public ModelAndView initSettings() {
        ModelAndView window = new ModelAndView();
        if(adminSettingLogic.proc(window)) {
            window.setViewName("complete");
        } else {
            window.setViewName("error");
        }
        return window;
    }
    
    //임시용 초기 설정화면 제어용 data_settings
    @RequestMapping(value = "/data_settings", method = RequestMethod.GET)
    public ModelAndView initSettingsSecondPhase() {
        ModelAndView window = new ModelAndView();
        if(adminSettingLogic.procSecondPhase(window)) {
            window.setViewName("complete");
        } else {
            window.setViewName("error");
        }
        return window;
    }
    
    //임시용 초기 설정화면 제어용 data_saving
    @RequestMapping(value = "/data_saving", method = RequestMethod.GET)
    public ModelAndView dataSaving() {
        ModelAndView window = new ModelAndView();
        if(adminSettingLogic.procSavingData(window)) {
            window.setViewName("complete");
        } else {
            window.setViewName("error");
        }
        return window;
    }

}
