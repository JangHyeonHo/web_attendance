package com.attendance.pro.attendance.export;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 테넌트 근태 보고서 설정 — 결재(도장)란 표시 on/off. 행 없음 = 미표시(false). */
@Service
public class ReportSettingService {

    private final ReportSettingMapper mapper;

    public ReportSettingService(ReportSettingMapper mapper) {
        this.mapper = mapper;
    }

    /** 결재란 표시 여부(미설정이면 false). */
    public boolean stampEnabled(long tenantId) {
        Boolean v = mapper.findStampEnabled(tenantId);
        return v != null && v;
    }

    @Transactional
    public boolean setStampEnabled(long tenantId, boolean enabled) {
        mapper.upsert(tenantId, enabled);
        return enabled;
    }

    /** 가산수당 적용 여부(§56) — 미설정이면 기본 TRUE(≥5인 가정, 보수적 준법). */
    public boolean premiumEnabled(long tenantId) {
        Boolean v = mapper.findPremiumEnabled(tenantId);
        return v == null || v;
    }

    @Transactional
    public boolean setPremiumEnabled(long tenantId, boolean enabled) {
        mapper.upsertPremium(tenantId, enabled);
        return enabled;
    }
}
