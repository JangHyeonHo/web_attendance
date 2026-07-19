package com.attendance.pro.attendance.export;

import com.attendance.pro.attendance.AttendanceDtos.MonthlyResponse;

/**
 * 근태 Excel(.xlsx) 내보내기 <b>확장점</b>.
 *
 * <p>기본 구현({@code default})은 정해진 표준 서식을 만든다. 회사(테넌트)마다 요청 서식이 다를 수 있으므로,
 * 새 서식이 필요하면 이 인터페이스를 구현한 {@code @Component}를 <b>다른 {@link #key()}</b>로 추가하기만 하면
 * 자동 등록된다({@link AttendanceExporters}가 수집). 어떤 테넌트가 어떤 exporter를 쓸지는
 * {@link AttendanceExporters#keyForTenant(long)}에서 결정한다(향후 테넌트 설정/entitlement 연동).
 *
 * <p>코어는 이 계약(SPI)만 안정적으로 유지하면 되고, 커스텀 서식이 늘어도 코어 코드는 바뀌지 않는다.
 */
public interface AttendanceExcelExporter {

    /** 이 exporter를 식별하는 키. 표준 서식은 {@code "default"}. 커스텀은 고유 키(예: 회사코드). */
    String key();

    /** 월별 근태를 .xlsx 바이트로 직렬화한다. */
    byte[] toXlsx(MonthlyResponse monthly, ExportMeta meta);
}
