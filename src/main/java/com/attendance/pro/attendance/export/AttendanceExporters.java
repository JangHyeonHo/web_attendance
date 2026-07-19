package com.attendance.pro.attendance.export;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * 등록된 근태 exporter들을 key로 모아, 테넌트에 맞는 것을 골라준다.
 *
 * <p>확장 방법: {@link AttendanceExcelExporter}를 새 key로 구현한 {@code @Component}를 추가하면 Spring이
 * 자동 주입해 여기에 등록된다(코어 수정 불필요). 테넌트→exporter 매핑만 {@link #keyForTenant(long)}에서 정한다.
 */
@Component
public class AttendanceExporters {

    private final Map<String, AttendanceExcelExporter> byKey;

    public AttendanceExporters(List<AttendanceExcelExporter> exporters) {
        this.byKey = exporters.stream()
                .collect(Collectors.toMap(AttendanceExcelExporter::key, Function.identity()));
    }

    /** 테넌트가 쓸 exporter(지정 없으면 default). */
    public AttendanceExcelExporter forTenant(long tenantId) {
        AttendanceExcelExporter picked = byKey.get(keyForTenant(tenantId));
        return picked != null ? picked : byKey.get("default");
    }

    /**
     * 테넌트별 exporter 선택 규칙(확장점). 현재는 표준(default) 고정 —
     * 향후 tenant 설정/entitlement에서 커스텀 서식 key를 읽어 반환하도록 확장한다.
     */
    private String keyForTenant(long tenantId) {
        return "default";
    }
}
