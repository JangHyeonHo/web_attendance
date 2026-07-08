package com.attendance.pro.controller;

import static com.attendance.pro.common.Redirector.redirect;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.attendance.pro.common.AdminSettingLogic;
import com.attendance.pro.dto.LanguageMasterDto;

/**
 * 관리자용 컨트롤러.
 * 언어 마스터 등록 화면과 초기 DB 셋업(테이블 생성/데이터 투입/백업)용 화면을 제공한다.
 */
@Controller
public class AdminController {

    private final AdminSettingLogic adminSettingLogic;

    public AdminController(AdminSettingLogic adminSettingLogic) {
        this.adminSettingLogic = adminSettingLogic;
    }

    @GetMapping("/")
    public String root() {
        return redirect("login");
    }

    /**
     * 관리자용 언어 마스터 설정 화면.
     */
    @GetMapping("/lang_mst")
    public ModelAndView languageSettings(@RequestParam(name = "lang", required = false) String lang,
            @RequestParam(name = "window", required = false) String windowId) {
        ModelAndView window = new ModelAndView();
        window.addObject("langs", adminSettingLogic.getAllLangs(lang, windowId));
        window.addObject("windowIds", WindowManagement.getAllWindowId());
        window.addObject("languages", WindowManagement.getAllLangs());
        window.setViewName("root/lang_mst");
        return window;
    }

    /**
     * 관리자용 언어 마스터 등록.
     */
    @PostMapping("/lang_mst")
    @ResponseBody
    public String registLangMst(LanguageMasterDto dto) {
        if (dto.getWindowId().isEmpty()
                || dto.getFrontLang().isEmpty()
                || dto.getLang().isEmpty()
                || dto.getLangValue().isEmpty()) {
            return "er";
        }
        int result = adminSettingLogic.insertLangMst(dto);
        return result == 0 ? "er" : "suc";
    }

    /**
     * 임시용 초기 설정화면 제어용 - DB 테이블 생성/변경.
     */
    @GetMapping("/admin_settings")
    public ModelAndView initSettings() {
        ModelAndView window = new ModelAndView();
        window.setViewName(adminSettingLogic.proc(window) ? "complete" : "error");
        return window;
    }

    /**
     * 임시용 초기 설정화면 제어용 - 초기 데이터 투입.
     */
    @GetMapping("/data_settings")
    public ModelAndView initSettingsSecondPhase() {
        ModelAndView window = new ModelAndView();
        window.setViewName(adminSettingLogic.procSecondPhase(window) ? "complete" : "error");
        return window;
    }

    /**
     * 임시용 초기 설정화면 제어용 - 데이터 백업(tsv 저장).
     */
    @GetMapping("/data_saving")
    public ModelAndView dataSaving() {
        ModelAndView window = new ModelAndView();
        window.setViewName(adminSettingLogic.procSavingData(window) ? "complete" : "error");
        return window;
    }

}
