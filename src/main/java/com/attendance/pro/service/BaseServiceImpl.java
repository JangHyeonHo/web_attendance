package com.attendance.pro.service;

import java.util.Map;

public interface BaseServiceImpl {
    
    public Map<String, Object> proc(Map<String, Object> data, 
            Map<String, Object> resData, 
            String... props);

}
