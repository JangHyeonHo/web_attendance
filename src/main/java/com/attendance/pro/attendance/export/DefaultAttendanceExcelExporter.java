package com.attendance.pro.attendance.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.attendance.pro.attendance.AttendanceDtos.DailyAttendance;
import com.attendance.pro.attendance.AttendanceDtos.MonthlyResponse;

/**
 * 표준 근태 Excel 서식(default). 한 달 전 일자 표 + 월 합계 행.
 * 근무 시간은 소수 시간 단위(예: 468분 → 7.80), 휴식은 분 단위. 헤더 음영 + 전 셀 테두리.
 * 회사별 다른 서식이 필요하면 이 클래스를 복제하지 말고 {@link AttendanceExcelExporter}를 다른 key로 새로 구현한다.
 */
@Component
public class DefaultAttendanceExcelExporter implements AttendanceExcelExporter {

    private static final String[] HEADERS = {
        "날짜", "구분", "예정 출근", "예정 퇴근", "실제 출근", "실제 퇴근",
        "예정 근무 시간", "휴식 시간 (분)", "실제 근무 시간", "비고"
    };
    private static final int COLS = HEADERS.length;

    @Override
    public String key() {
        return "default";
    }

    @Override
    public byte[] toXlsx(MonthlyResponse monthly, ExportMeta meta) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("근태");

            CellStyle titleStyle = titleStyle(wb);
            CellStyle headStyle = headerStyle(wb);
            CellStyle textStyle = borderedText(wb, false);
            CellStyle hourStyle = borderedHours(wb, false);
            CellStyle totalText = borderedText(wb, true);
            CellStyle totalHour = borderedHours(wb, true);

            //제목
            Row title = sheet.createRow(0);
            cell(title, 0, String.format("%d년 %d월 근태기록 — %s (%s)",
                    meta.year(), meta.month(), nvl(meta.memberName()), nvl(meta.tenantName())), titleStyle);

            //헤더(row 2)
            Row head = sheet.createRow(2);
            for (int c = 0; c < COLS; c++) {
                cell(head, c, HEADERS[c], headStyle);
            }

            //데이터(한 달 전 일자)
            int r = 3;
            for (DailyAttendance d : monthly.days()) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < COLS; c++) {
                    row.createCell(c).setCellStyle(textStyle); //빈 칸도 테두리 유지
                }
                text(row, 0, d.date() == null ? "" : d.date().toString());
                text(row, 1, category(d));
                text(row, 2, nvl(d.scheduleStart()));
                text(row, 3, nvl(d.scheduleEnd()));
                text(row, 4, nvl(d.stampIn()));
                text(row, 5, nvl(d.stampOut()));
                hours(row, 6, d.scheduledMinutes(), hourStyle);
                minutes(row, 7, d.recognizedBreakMinutes());
                hours(row, 8, d.workMinutes(), hourStyle);
                text(row, 9, d.manual() ? "O" : "");
            }

            //합계
            Row total = sheet.createRow(r + 1);
            for (int c = 0; c < COLS; c++) {
                total.createCell(c).setCellStyle(totalText);
            }
            total.getCell(0).setCellValue("합계");
            hours(total, 6, monthly.totalScheduledMinutes(), totalHour);
            total.getCell(7).setCellValue(monthly.totalBreakMinutes());
            hours(total, 8, monthly.totalWorkMinutes(), totalHour);

            for (int c = 0; c < COLS; c++) {
                sheet.autoSizeColumn(c);
            }
            return write(wb, out);
        } catch (IOException e) {
            throw new UncheckedIOException("attendance xlsx export failed", e);
        }
    }

    private static byte[] write(Workbook wb, ByteArrayOutputStream out) throws IOException {
        wb.write(out);
        return out.toByteArray();
    }

    private static String category(DailyAttendance d) {
        if (d.holiday()) {
            return d.holidayName() == null ? "공휴일" : d.holidayName();
        }
        return d.dayOff() ? "휴무" : "근무";
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    /** 이미 생성·스타일된 셀에 문자열만 채운다. */
    private static void text(Row row, int col, String value) {
        row.getCell(col).setCellValue(value);
    }

    /** 분 → 소수 시간(예: 468 → 7.8). null이면 빈 칸. */
    private static void hours(Row row, int col, Integer minutes, CellStyle style) {
        Cell c = row.getCell(col);
        if (minutes != null) {
            c.setCellValue(minutes / 60.0);
        }
        c.setCellStyle(style);
    }

    private static void minutes(Row row, int col, Integer value) {
        if (value != null) {
            row.getCell(col).setCellValue(value);
        }
    }

    // ---- 스타일 ----

    private static void border(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    private static CellStyle titleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 12);
        s.setFont(f);
        return s;
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        border(s);
        return s;
    }

    private static CellStyle borderedText(Workbook wb, boolean bold) {
        CellStyle s = wb.createCellStyle();
        if (bold) {
            Font f = wb.createFont();
            f.setBold(true);
            s.setFont(f);
        }
        border(s);
        return s;
    }

    private static CellStyle borderedHours(Workbook wb, boolean bold) {
        CellStyle s = borderedText(wb, bold);
        s.setDataFormat(wb.createDataFormat().getFormat("0.00")); //소수 시간
        return s;
    }
}
