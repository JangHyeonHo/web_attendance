package com.attendance.pro.other;

public class CodeMap {
    
    public final static String STRING_TRUE = "1";
    public final static String ERROR = "E";
    public final static String SUCCESS = "S";
    public final static String RES = "res";
    public final static String MSG = "msg";
    public final static String RED= "redirect";
    

    public final static String Korean = "KOR";
    public final static String English = "ENG";
    
    /**
     * NullPointerException대응용 isEqual함수 
     * @param a
     * @param b
     * @return
     */
    public static boolean isEqual(String a, String b) {
        if(a==null && b==null) return true;
        if(a==null && b!=null) return false;
        if(a!=null && b==null) return false;
        return a.equals(b);
    }
    public static boolean isEqual(Object a, Object b) {
        if(a==null && b==null) return true;
        if(a==null && b!=null) return false;
        if(a!=null && b==null) return false;
        return a.equals(b);
    }
    public static boolean isEqual(Integer a, Integer b) {
        if(a==null && b==null) return true;
        if(a==null && b!=null) return false;
        if(a!=null && b==null) return false;
        return a.equals(b);
    }
    public static boolean isEqual(Double a, Double b) {
        if(a==null && b==null) return true;
        if(a==null && b!=null) return false;
        if(a!=null && b==null) return false;
        return a.equals(b);
    }
    public static boolean isEqual(Long a, Long b) {
        if(a==null && b==null) return true;
        if(a==null && b!=null) return false;
        if(a!=null && b==null) return false;
        return a.equals(b);
    }
    public static boolean isEqual(Float a, Float b) {
        if(a==null && b==null) return true;
        if(a==null && b!=null) return false;
        if(a!=null && b==null) return false;
        return a.equals(b);
    }
    public static boolean isEqual(Character a, Character b) {
        if(a==null && b==null) return true;
        if(a==null && b!=null) return false;
        if(a!=null && b==null) return false;
        return a.equals(b);
    }
    public static boolean isEqual(Short a, Short b) {
        if(a==null && b==null) return true;
        if(a==null && b!=null) return false;
        if(a!=null && b==null) return false;
        return a.equals(b);
    }
    public static boolean isEqual(Byte a, Byte b) {
        if(a==null && b==null) return true;
        if(a==null && b!=null) return false;
        if(a!=null && b==null) return false;
        return a.equals(b);
    }
    public static boolean isEqual(Boolean a, Boolean b) {
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
    
    public static boolean isEqualMultyisOne(Object a, Object... b) {
        for(Object c : b) {
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
