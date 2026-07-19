package com.attendance.pro.attendance.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.attendance.pro.attendance.AttendanceDtos.DailyAttendance;
import com.attendance.pro.attendance.AttendanceDtos.MonthlyResponse;

/**
 * 표준 근태 Excel 서식(default). 일자별 표 + 월 합계 행.
 * 회사별 다른 서식이 필요하면 이 클래스를 복제하지 말고 {@link AttendanceExcelExporter}를 다른 key로 새로 구현한다.
 */
@Component
public class DefaultAttendanceExcelExporter implements AttendanceExcelExporter {

    private static final String[] HEADERS = {
        "날짜", "구분", "스케줄", "출근", "퇴근",
        "예정근무(분)", "인정휴게(분)", "실근무(분)", "정정"
    };

    @Override
    public String key() {
        return "default";
    }

    @Override
    public byte[] toXlsx(MonthlyResponse monthly, ExportMeta meta) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("근태");
            CellStyle bold = boldStyle(wb);
            CellStyle header = headerStyle(wb);

            //제목 행
            Row title = sheet.createRow(0);
            cell(title, 0, String.format("%d년 %d월 근태기록 — %s (%s)",
                    meta.year(), meta.month(), nvl(meta.memberName()), nvl(meta.tenantName())), bold);

            //헤더 행
            Row head = sheet.createRow(2);
            for (int c = 0; c < HEADERS.length; c++) {
                cell(head, c, HEADERS[c], header);
            }

            //데이터 행
            int r = 3;
            for (DailyAttendance d : monthly.days()) {
                Row row = sheet.createRow(r++);
                cell(row, 0, d.date() == null ? "" : d.date().toString(), null);
                cell(row, 1, category(d), null);
                cell(row, 2, schedule(d), null);
                cell(row, 3, nvl(d.stampIn()), null);
                cell(row, 4, nvl(d.stampOut()), null);
                num(row, 5, d.scheduledMinutes());
                num(row, 6, d.recognizedBreakMinutes());
                num(row, 7, d.workMinutes());
                cell(row, 8, d.manual() ? "정정" : "", null);
            }

            //합계 행
            Row total = sheet.createRow(r + 1);
            cell(total, 0, "합계", bold);
            numStyled(total, 5, monthly.totalScheduledMinutes(), bold);
            numStyled(total, 6, monthly.totalBreakMinutes(), bold);
            numStyled(total, 7, monthly.totalWorkMinutes(), bold);

            for (int c = 0; c < HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("attendance xlsx export failed", e);
        }
    }

    private static String category(DailyAttendance d) {
        if (d.holiday()) {
            return d.holidayName() == null ? "공휴일" : d.holidayName();
        }
        return d.dayOff() ? "휴무" : "근무";
    }

    private static String schedule(DailyAttendance d) {
        if (d.scheduleStart() == null || d.scheduleEnd() == null) {
            return "";
        }
        return d.scheduleStart() + "~" + d.scheduleEnd();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    /** 정수(분) 셀 — null이면 빈 칸. Excel에서 합계 가능하도록 숫자로 기록. */
    private static void num(Row row, int col, Integer value) {
        Cell c = row.createCell(col);
        if (value != null) {
            c.setCellValue(value);
        }
    }

    private static void numStyled(Row row, int col, int value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static CellStyle boldStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = boldStyle(wb);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }
}
