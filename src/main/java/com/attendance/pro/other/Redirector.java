package com.attendance.pro.other;

public class Redirector {
    
    public static String redirect(String url) {
        if(url != null && !url.isEmpty()) {
            return "redirect:"+url;
        }
        return "redirect:/";
    }
    
}
