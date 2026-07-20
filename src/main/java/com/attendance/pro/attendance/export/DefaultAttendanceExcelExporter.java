package com.attendance.pro.attendance.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.attendance.pro.attendance.AttendanceDtos.DailyAttendance;
import com.attendance.pro.attendance.AttendanceDtos.MonthlyResponse;

/**
 * 표준 근태 Excel 서식(default). 상단 머리(날짜·회사명·이름 라벨/값) + 우상단 결재(도장)란(옵션),
 * 한 달 전 일자 표(헤더 음영 + 전 셀 테두리), 근무는 소수 시간·휴식은 분, 월 합계 행.
 * 기본 폰트는 언어별(한:맑은 고딕, 일:Meiryo UI). 회사별 다른 서식은 다른 key로 새로 구현한다.
 */
@Component
public class DefaultAttendanceExcelExporter implements AttendanceExcelExporter {

    private static final String[] HEADERS = {
        "날짜", "구분", "예정 출근", "예정 퇴근", "실제 출근", "실제 퇴근",
        "예정 근무 시간", "휴식 시간", "실제 근무 시간", "비고"
    };
    private static final int COLS = HEADERS.length;
    private static final int HEADER_ROW = 5;         //상단 머리(0~3)·공백(4) 다음
    private static final byte[] HEADER_FILL = {(byte) 0xD9, (byte) 0xE1, (byte) 0xF2}; //연한 블루그레이
    private static final byte[] LABEL_FILL = {(byte) 0xF2, (byte) 0xF2, (byte) 0xF2};  //연회색
    private static final byte[] SAT_FILL = {(byte) 0xE7, (byte) 0xEF, (byte) 0xFB};    //토요일 옅은 파랑
    private static final byte[] SUN_FILL = {(byte) 0xFB, (byte) 0xE8, (byte) 0xE8};    //일요일·공휴일 옅은 빨강

    @Override
    public String key() {
        return "default";
    }

    @Override
    public byte[] toXlsx(MonthlyResponse monthly, ExportMeta meta) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("근태");
            String font = fontFor(meta.lang());

            XSSFCellStyle label = base(wb, font, true);
            fill(label, LABEL_FILL);
            XSSFCellStyle value = base(wb, font, false);
            XSSFCellStyle header = center(base(wb, font, true));
            fill(header, HEADER_FILL);
            XSSFCellStyle text = textStyle(wb, font, null);
            XSSFCellStyle textSat = textStyle(wb, font, SAT_FILL);
            XSSFCellStyle textSun = textStyle(wb, font, SUN_FILL);
            XSSFCellStyle hour = hourStyle(wb, font, null);
            XSSFCellStyle hourSat = hourStyle(wb, font, SAT_FILL);
            XSSFCellStyle hourSun = hourStyle(wb, font, SUN_FILL);
            XSSFCellStyle totalText = base(wb, font, true);
            XSSFCellStyle totalHour = base(wb, font, true);
            totalHour.setDataFormat(wb.createDataFormat().getFormat("0.00"));

            //상단 머리 — A=라벨 / B=값 (날짜·회사명·부서명·이름)
            put(sheet, 0, 0, "날짜", label);
            put(sheet, 0, 1, meta.issueDate() == null ? "" : meta.issueDate().toString(), value);
            put(sheet, 1, 0, "회사명", label);
            put(sheet, 1, 1, nvl(meta.tenantName()), value);
            put(sheet, 2, 0, "부서명", label);
            put(sheet, 2, 1, nvl(meta.department()), value);
            put(sheet, 3, 0, "이름", label);
            put(sheet, 3, 1, nvl(meta.memberName()), value);

            if (meta.stampArea()) {
                stampBox(sheet, wb, font);
            }

            //표 헤더
            Row head = sheet.createRow(HEADER_ROW);
            head.setHeightInPoints(24); //헤더는 조금 더 높게
            for (int c = 0; c < COLS; c++) {
                cell(head, c, HEADERS[c], header);
            }

            //데이터(한 달 전 일자) — 토(옅은 파랑)·일/공휴일(옅은 빨강) 행 배경
            int r = HEADER_ROW + 1;
            for (DailyAttendance d : monthly.days()) {
                Row row = sheet.createRow(r++);
                row.setHeightInPoints(20); //행 간격 넉넉하게(한눈에 보기 편하게)
                boolean red = d.holiday() || (d.date() != null && d.date().getDayOfWeek() == DayOfWeek.SUNDAY);
                boolean blue = !red && d.date() != null && d.date().getDayOfWeek() == DayOfWeek.SATURDAY;
                XSSFCellStyle rowText = red ? textSun : blue ? textSat : text;
                XSSFCellStyle rowHour = red ? hourSun : blue ? hourSat : hour;
                for (int c = 0; c < COLS; c++) {
                    row.createCell(c).setCellStyle(rowText); //빈 칸도 테두리·배경 유지
                }
                row.getCell(0).setCellValue(d.date() == null ? "" : d.date().toString());
                row.getCell(1).setCellValue(category(d));
                row.getCell(2).setCellValue(nvl(d.scheduleStart()));
                row.getCell(3).setCellValue(nvl(d.scheduleEnd()));
                row.getCell(4).setCellValue(nvl(d.stampIn()));
                row.getCell(5).setCellValue(nvl(d.stampOut()));
                setHours(row.getCell(6), d.scheduledMinutes(), rowHour);
                setHours(row.getCell(7), d.recognizedBreakMinutes(), rowHour); //휴식도 소수 시간
                setHours(row.getCell(8), d.workMinutes(), rowHour);
                row.getCell(9).setCellValue(nvl(d.note())); //비고 — 실제 정정 사유
            }

            //합계
            Row total = sheet.createRow(r + 1);
            total.setHeightInPoints(20);
            for (int c = 0; c < COLS; c++) {
                total.createCell(c).setCellStyle(totalText);
            }
            total.getCell(0).setCellValue("합계");
            setHours(total.getCell(6), monthly.totalScheduledMinutes(), totalHour);
            setHours(total.getCell(7), monthly.totalBreakMinutes(), totalHour);
            setHours(total.getCell(8), monthly.totalWorkMinutes(), totalHour);

            //autoSizeColumn은 AWT 폰트 메트릭에 의존 — 헤드리스·폰트 미설치 환경에서 예외가 날 수 있어
            //방어적으로 감싸고, 실패 시 고정 너비로 폴백한다(다운로드 자체가 500으로 죽지 않게).
            for (int c = 0; c < COLS; c++) {
                try {
                    sheet.autoSizeColumn(c);
                    //내용폭 + 여유(약 4자), 최소 12자 보장 · 과도한 폭은 상한 — 열 간격이 너무 좁지 않게
                    int w = Math.max(sheet.getColumnWidth(c) + 4 * 256, 12 * 256);
                    sheet.setColumnWidth(c, Math.min(w, 60 * 256));
                } catch (RuntimeException e) {
                    sheet.setColumnWidth(c, 16 * 256); //폴백도 넉넉하게
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("attendance xlsx export failed", e);
        }
    }

    /** 우상단 결재(도장)란: [결재 | 인사담당자 | 총괄담당자], 아래 칸은 인쇄 후 날인용 빈 칸. */
    private static void stampBox(XSSFSheet sheet, XSSFWorkbook wb, String font) {
        int c0 = COLS - 3; //7,8,9
        XSSFCellStyle head = center(base(wb, font, true));
        fill(head, LABEL_FILL);
        XSSFCellStyle blank = base(wb, font, false);

        //머리(라벨 4행)와 높이를 맞춰 결재란도 0~3행에 배치
        Row r0 = row(sheet, 0);
        cell(r0, c0, "결재", head);
        cell(r0, c0 + 1, "인사담당자", head);
        cell(r0, c0 + 2, "총괄담당자", head);
        for (int rr = 1; rr <= 3; rr++) {
            Row row = row(sheet, rr);
            cell(row, c0, "", head);
            cell(row, c0 + 1, "", blank);
            cell(row, c0 + 2, "", blank);
        }
        merge(sheet, 0, 3, c0, c0);         //결재 라벨(세로 병합)
        merge(sheet, 1, 3, c0 + 1, c0 + 1); //인사담당자 날인칸
        merge(sheet, 1, 3, c0 + 2, c0 + 2); //총괄담당자 날인칸
        for (int rr = 1; rr <= 3; rr++) {
            row(sheet, rr).setHeightInPoints(24);
        }
    }

    private static void merge(XSSFSheet sheet, int r1, int r2, int c1, int c2) {
        CellRangeAddress region = new CellRangeAddress(r1, r2, c1, c2);
        sheet.addMergedRegion(region);
        RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
    }

    private static Row row(XSSFSheet sheet, int i) {
        Row r = sheet.getRow(i);
        return r != null ? r : sheet.createRow(i);
    }

    private static void put(XSSFSheet sheet, int r, int c, String v, XSSFCellStyle style) {
        cell(row(sheet, r), c, v, style);
    }

    private static void cell(Row row, int col, String value, XSSFCellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void setHours(Cell c, Integer minutes, XSSFCellStyle style) {
        if (minutes != null) {
            c.setCellValue(minutes / 60.0); //분 → 소수 시간
        }
        c.setCellStyle(style);
    }

    private static String category(DailyAttendance d) {
        if (d.holiday()) {
            return d.holidayName() == null ? "공휴일" : d.holidayName();
        }
        if (d.leaveName() != null) {
            return d.leaveName(); //승인 휴가(#9)
        }
        return d.dayOff() ? "휴무" : "근무";
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 언어별 기본 폰트 — 한:맑은 고딕, 일:Meiryo UI, 그 외:맑은 고딕(라틴 포함 무난). */
    private static String fontFor(String lang) {
        if ("JPN".equalsIgnoreCase(lang)) {
            return "Meiryo UI";
        }
        return "맑은 고딕";
    }

    // ---- 스타일 helper ----

    private static XSSFCellStyle base(XSSFWorkbook wb, String fontName, boolean bold) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontName(fontName);
        f.setBold(bold);
        s.setFont(f);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private static XSSFCellStyle center(XSSFCellStyle s) {
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    /** 테두리 텍스트 셀(옵션 배경). */
    private static XSSFCellStyle textStyle(XSSFWorkbook wb, String font, byte[] fillRgb) {
        XSSFCellStyle s = base(wb, font, false);
        if (fillRgb != null) {
            fill(s, fillRgb);
        }
        return s;
    }

    /** 소수 시간(0.00) 셀(옵션 배경). */
    private static XSSFCellStyle hourStyle(XSSFWorkbook wb, String font, byte[] fillRgb) {
        XSSFCellStyle s = base(wb, font, false);
        s.setDataFormat(wb.createDataFormat().getFormat("0.00"));
        if (fillRgb != null) {
            fill(s, fillRgb);
        }
        return s;
    }

    private static void fill(XSSFCellStyle s, byte[] rgb) {
        s.setFillForegroundColor(new XSSFColor(rgb, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }
}
