package com.attendance.pro.dto;

public class LanguageMasterDto extends BaseDto {
    
    /**
     * 
     */
    private static final long serialVersionUID = -6477122902675738944L;
    /**
     * windowId, frontLang, optionValue, lang = PK
     */
    private String windowId;        //화면 아이디
    private String frontLang;       //표기 언어값
    private Integer optionValue;    //옵션 값
    private String lang;                //언어
    private String langValue;       //해당 언어 값
    
    public String getWindowId() {
        return windowId;
    }
    public void setWindowId(String windowId) {
        this.windowId = windowId;
    }
    public String getFrontLang() {
        return frontLang;
    }
    public void setFrontLang(String frontLang) {
        this.frontLang = frontLang;
    }
    public Integer getOptionValue() {
        return optionValue;
    }
    public void setOptionValue(Integer optionValue) {
        this.optionValue = optionValue;
    }
    public String getLang() {
        return lang;
    }
    public void setLang(String lang) {
        this.lang = lang;
    }
    public String getLangValue() {
        return langValue;
    }
    public void setLangValue(String langValue) {
        this.langValue = langValue;
    }
    

}
