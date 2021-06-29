package com.attendance.pro.other;

public class CodeMap {
    
    public final static String STRING_TRUE = "1";
    
    public static boolean isEqual(String a, String b) {
        if(a==null && b==null) return true;
        if(a==null && b!=null) return false;
        if(a!=null && b==null) return false;
        return a.equals(b);
    }
    
    //A가 B들중에서 하나라도 같은값이 있으면 TRUE
    public static boolean isEqualMultyisOne(String a, String... b) {
        for(String c : b) {
            if(isEqual(a,  c)) {
                return true;
            }
        }
        return false;
    }
    
    
    public static boolean isStringEqual(String value) {
        if(value==null) return false;
        return STRING_TRUE.equals(value);
    }
    
    public static boolean isEmpty(String a) {
        if(a==null) return true;
        return a.isEmpty();
    }
    
    public static boolean isEmpty(Object o) {
        if(o==null) return true;
        return false;
    }

}
