package com.attendance.pro.controller;

import static com.attendance.pro.other.Redirector.redirect;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.attendance.pro.dto.LanguageMasterDto;
import com.attendance.pro.other.AdminSettingLogic;


@Controller("/")
public class AdminController {
    
    @Autowired
    AdminSettingLogic adminSettingLogic;
    
    
    @RequestMapping(method = RequestMethod.GET)
    public String root() {
        return redirect("login");
    }
    
    //관리자용 DB설정용
    @GetMapping(value = "/lang_mst")
    public ModelAndView languageSettings(@RequestParam(name = "lang", required = false)String lang, 
            @RequestParam(name = "window", required = false)String windowId) {
        ModelAndView window = new ModelAndView();
        window.addObject("langs", adminSettingLogic.getAllLangs(lang, windowId));
        window.addObject("windowIds", WindowManagement.getAllWindowId());
        window.addObject("languages", WindowManagement.getAllLangs());
        window.setViewName("root/lang_mst");
        return window;
    }
    
    //관리자용 DB설정용
    @PostMapping(value = "/lang_mst")
    @ResponseBody
    public String registLangMst(LanguageMasterDto dto) {
        if(dto.getWindowId().isEmpty()) {
            return "er";
        }
        if(dto.getFrontLang().isEmpty()) {
            return "er";
        }
        if(dto.getLang().isEmpty()) {
            return "er";
        }
        if(dto.getLangValue().isEmpty()) {
            return "er";
        }
        int result = adminSettingLogic.insertLangMst(dto);
        if(result==0) return "er";
        return "suc";
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
