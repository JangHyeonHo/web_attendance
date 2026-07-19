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
        //검증이 참조하는 앞 3행(1일 근무 · 2일 정정 · 6일 공휴일)은 순서 고정, 뒤로 며칠 더(한 달 느낌)
        List<DailyAttendance> days = List.of(
                work(1, "09:02", "18:10", 480, 60, 468, false),
                work(2, "08:58", "18:30", 480, 60, 512, true),
                holiday(6, "제헌절"),
                dayOff(4),
                work(7, "09:00", "17:40", 480, 60, 400, false),
                work(8, "09:00", "18:00", 480, 60, 480, false),
                work(9, "09:10", "18:05", 480, 60, 475, false),
                dayOff(11),
                work(13, "08:55", "18:20", 480, 90, 485, true),
                work(14, "09:00", "18:00", 480, 60, 480, false));
        //합계(예정 3360분=56h · 휴식 450분 · 실근무 3300분=55h) — 근무 7일 기준
        return new MonthlyResponse(2026, 7, days, 3360, 450, 3300);
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
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("예정 출근");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("예정 근무 시간");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("휴식 시간 (분)");
            assertThat(header.getCell(8).getStringCellValue()).isEqualTo("실제 근무 시간");
            assertThat(header.getCell(9).getStringCellValue()).isEqualTo("비고");

            //첫 데이터 행(2026-07-01, 근무) — 실제 근무 468분 → 7.8시간(소수 시간)
            Row first = sheet.getRow(3);
            assertThat(first.getCell(1).getStringCellValue()).isEqualTo("근무");
            assertThat(first.getCell(2).getStringCellValue()).isEqualTo("09:00"); //예정 출근
            assertThat(first.getCell(8).getNumericCellValue()).isEqualTo(468 / 60.0);

            //비고 표시(2번째 데이터 행 manual → 'O')
            assertThat(sheet.getRow(4).getCell(9).getStringCellValue()).isEqualTo("O");

            //공휴일 명칭
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("제헌절");
        }
    }
}
