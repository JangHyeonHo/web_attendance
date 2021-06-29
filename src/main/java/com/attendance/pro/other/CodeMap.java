package com.attendance.pro.other;

public class CodeMap {
    
    public final static String STRING_TRUE = "1";
    
    public static boolean isEqual(String a, String b) {
        if(a==null && b==null) return true;
        if(a==null && b!=null) return false;
        if(a!=null && b==null) return false;
        return a.equals(b);
    }
    
    public static boolean isStringEqual(String value) {
        if(value==null) return false;
        return STRING_TRUE.equals(value);
    }

}
