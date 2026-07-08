package com.attendance.pro.common;

/**
 * 뷰 리다이렉트 문자열 생성 유틸리티.
 */
public final class Redirector {

    private Redirector() {
    }

    public static String redirect(String url) {
        if (url != null && !url.isEmpty()) {
            return "redirect:" + url;
        }
        return "redirect:/";
    }

}
