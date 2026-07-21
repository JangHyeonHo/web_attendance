package com.attendance.pro.attendance.export;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.pro.attendance.export.ReportSettingMapper.StampImageRow;
import com.attendance.pro.common.ApiException;

/** 테넌트 근태 보고서 설정 — 결재(도장)란 표시 on/off. 행 없음 = 미표시(false). */
@Service
public class ReportSettingService {

    /** 도장 이미지 상한 — 레이아웃 붕괴/저장 부담 방지. */
    private static final int STAMP_MAX_BYTES = 200 * 1024;
    private static final int STAMP_MAX_DIM = 300;
    private static final Set<String> STAMP_MIME = Set.of("image/png", "image/jpeg");
    private static final Set<String> STAMP_SIZES = Set.of("SMALL", "MEDIUM", "LARGE");

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

    /** 도장 표시 크기(SMALL|MEDIUM|LARGE) — 미설정이면 MEDIUM. */
    public String stampSize(long tenantId) {
        String s = mapper.findStampSize(tenantId);
        return s == null ? "MEDIUM" : s;
    }

    @Transactional
    public void setStampSize(long tenantId, String size) {
        String s = size == null ? "MEDIUM" : size.toUpperCase(java.util.Locale.ROOT);
        if (!STAMP_SIZES.contains(s)) {
            s = "MEDIUM";
        }
        mapper.upsertStampSize(tenantId, s);
    }

    /** 도장 이미지(bytes+mime) — 미등록이면 null. */
    public StampImageRow stampImage(long tenantId) {
        StampImageRow r = mapper.findStampImage(tenantId);
        return (r == null || r.image() == null) ? null : r;
    }

    /** 도장 이미지를 base64로 받아 검증 후 저장. PNG/JPEG · ≤300×300px · ≤200KB. */
    @Transactional
    public void setStampImage(long tenantId, String base64, String mime) {
        String m = mime == null ? "" : mime.trim().toLowerCase(java.util.Locale.ROOT);
        if (m.equals("image/jpg")) {
            m = "image/jpeg";
        }
        if (!STAMP_MIME.contains(m)) {
            throw ApiException.badRequest("STAMP_IMAGE_INVALID", "report.stamp.invalid");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(stripDataUrl(base64));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("STAMP_IMAGE_INVALID", "report.stamp.invalid");
        }
        if (bytes.length == 0 || bytes.length > STAMP_MAX_BYTES) {
            throw ApiException.badRequest("STAMP_TOO_BIG", "report.stamp.too-big");
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            img = null;
        }
        if (img == null) {
            throw ApiException.badRequest("STAMP_IMAGE_INVALID", "report.stamp.invalid");
        }
        if (img.getWidth() > STAMP_MAX_DIM || img.getHeight() > STAMP_MAX_DIM) {
            throw ApiException.badRequest("STAMP_TOO_BIG", "report.stamp.too-big");
        }
        mapper.upsertStampImage(tenantId, bytes, m);
    }

    @Transactional
    public void clearStampImage(long tenantId) {
        mapper.clearStampImage(tenantId);
    }

    /** 도장 이미지를 data URL로(응답·인쇄용). 미등록이면 null. */
    public String stampImageDataUrl(long tenantId) {
        StampImageRow r = stampImage(tenantId);
        if (r == null) {
            return null;
        }
        return "data:" + r.mime() + ";base64," + Base64.getEncoder().encodeToString(r.image());
    }

    private static String stripDataUrl(String s) {
        if (s == null) {
            return "";
        }
        int comma = s.indexOf(',');
        return (s.startsWith("data:") && comma >= 0) ? s.substring(comma + 1) : s;
    }
}
