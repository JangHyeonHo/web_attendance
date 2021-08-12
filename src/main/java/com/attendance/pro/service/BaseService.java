package com.attendance.pro.service;

public class BaseService {
    
    protected String objectToString(Object e) {
        if(e==null) {
            return null;
        }
        if(e instanceof String) {
            return String.valueOf(e);
        }
        return null;
    }
    
    protected Integer objectToInteger(Object e) {
        String s = objectToString(e);
        if(e instanceof Integer) {
            return Integer.valueOf(s);
        }
        return null;
    }
    
    protected Boolean objectToBoolean(Object e) {
        String s = objectToString(e);
        if(e instanceof Boolean) {
            return Boolean.valueOf(s);
        }
        return null;
    }
    
    protected Double objectToDouble(Object e) {
        String s = objectToString(e);
        if(e instanceof Double) {
            return Double.valueOf(s);
        }
        return null;
    }

}
