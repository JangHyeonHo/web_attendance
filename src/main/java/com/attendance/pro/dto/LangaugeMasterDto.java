package com.attendance.pro.dto;

public class LangaugeMasterDto extends BaseDto {
    
    /**
     * 
     */
    private static final long serialVersionUID = -6477122902675738944L;
    /**
     * windowId, masterName, lang = PK
     */
    private String windowId;        //화면 아이디
    private String masterName;   //언어명
    private String lang;                 //언어
    private String langValue;       //해당 언어 값
    
    public String getWindowId() {
        return windowId;
    }
    public void setWindowId(String windowId) {
        this.windowId = windowId;
    }
    public String getMasterName() {
        return masterName;
    }
    public void setMasterName(String masterName) {
        this.masterName = masterName;
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
