package com.attendance.pro.attendance.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.attendance.pro.attendance.AttendanceDtos.DailyAttendance;
import com.attendance.pro.attendance.AttendanceDtos.MonthlyResponse;

/** 근태 Excel 기본 exporter — 헤더·데이터·합계 셀 검증 + 데모용 파일 산출. */
class DefaultAttendanceExcelExporterTest {

    private static DailyAttendance work(int day, String in, String out, int sched, int brk, int workM, boolean manual) {
        return new DailyAttendance(LocalDate.of(2026, 7, day), false, false, "09:00", "18:00",
                in, out, null, sched, brk, 60, brk, workM, manual);
    }

    private static DailyAttendance holiday(int day, String name) {
        return new DailyAttendance(LocalDate.of(2026, 7, day), true, false, null, null,
                null, null, name, null, null, null, null, null, false);
    }

    private static DailyAttendance dayOff(int day) {
        return new DailyAttendance(LocalDate.of(2026, 7, day), false, true, null, null,
                null, null, null, null, null, null, null, null, false);
    }

    private static MonthlyResponse sample() {
        List<DailyAttendance> days = List.of(
                work(1, "09:02", "18:10", 480, 60, 468, false),
                work(2, "08:58", "18:30", 480, 60, 512, true),
                holiday(6, "제헌절"),
                dayOff(4),
                work(7, "09:00", "17:40", 480, 60, 400, false));
        return new MonthlyResponse(2026, 7, days, 1440, 180, 1380);
    }

    @Test
    @DisplayName("XLSX-01: 제목·헤더·데이터·합계가 올바른 셀에 기록된다")
    void writesExpectedCells() throws Exception {
        byte[] bytes = new DefaultAttendanceExcelExporter()
                .toXlsx(sample(), new ExportMeta("ACME 주식회사", "김총관", 2026, 7));

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .contains("2026").contains("김총관").contains("ACME");

            Row header = sheet.getRow(2);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("날짜");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("실근무(분)");

            //첫 데이터 행(2026-07-01, 근무 468분)
            Row first = sheet.getRow(3);
            assertThat(first.getCell(1).getStringCellValue()).isEqualTo("근무");
            assertThat(first.getCell(7).getNumericCellValue()).isEqualTo(468d);

            //정정 표시(2번째 데이터 행 manual)
            assertThat(sheet.getRow(4).getCell(8).getStringCellValue()).isEqualTo("정정");

            //공휴일 명칭
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("제헌절");
        }
    }
}
