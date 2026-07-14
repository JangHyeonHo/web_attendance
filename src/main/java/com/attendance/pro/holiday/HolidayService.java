package com.attendance.pro.holiday;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.holiday.HolidayDtos.HolidayCreateRequest;
import com.attendance.pro.holiday.HolidayDtos.HolidayResponse;
import com.attendance.pro.holiday.HolidayDtos.HolidaySyncResponse;
import com.attendance.pro.holiday.HolidayMapper.NationalHoliday;
import com.attendance.pro.tenant.ProfileCountry;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;

/**
 * 공휴일 CRUD + Nager.Date 동기화 서비스.
 *
 * 동기화 알고리즘(단일 트랜잭션 — holiday-plan §2-4):
 * 외부 호출·검증은 트랜잭션 밖(외부 IO 동안 커넥션을 잡지 않는다) → 검증 전부 통과 후
 * [해당 연도 NATIONAL만 삭제 → INSERT IGNORE(COMPANY 우선)]를 TransactionTemplate로 원자 실행.
 */
@Service
public class HolidayService {

    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);

    /** 명칭 컬럼 한계(실데이터에 없는 방어) */
    private static final int NAME_MAX_LENGTH = 100;
    /** 연간 국가 공휴일 상한(오염된 대량 응답 방어 — KR/JP 실측 15~20건) */
    private static final int MAX_HOLIDAYS_PER_YEAR = 100;

    private final HolidayMapper holidayMapper;
    private final TenantMapper tenantMapper;
    private final NagerDateClient nagerDateClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    //생성자가 2개(테스트용 Clock 주입)이므로 스프링이 쓸 쪽을 명시한다
    @Autowired
    public HolidayService(HolidayMapper holidayMapper, TenantMapper tenantMapper,
            NagerDateClient nagerDateClient, TransactionTemplate transactionTemplate) {
        this(holidayMapper, tenantMapper, nagerDateClient, transactionTemplate, Clock.systemDefaultZone());
    }

    HolidayService(HolidayMapper holidayMapper, TenantMapper tenantMapper,
            NagerDateClient nagerDateClient, TransactionTemplate transactionTemplate, Clock clock) {
        this.holidayMapper = holidayMapper;
        this.tenantMapper = tenantMapper;
        this.nagerDateClient = nagerDateClient;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * 국가 공휴일 동기화(TENANT_ADMIN 연도 지정). 허용 연도 = 현재 −1 ~ +2(§2-6).
     */
    public HolidaySyncResponse sync(long tenantId, int year) {
        validateYearRange(year);
        Tenant tenant = requireTenant(tenantId);
        ProfileCountry country = countryOf(tenant);
        //외부 호출 + 검증은 트랜잭션 밖 — 전부 통과해야 DB에 손댐(§2-3)
        List<NagerHoliday> raw = nagerDateClient.fetch(year, country.name());
        List<NationalHoliday> entries = filterAndValidate(raw, year, country.name());
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year + 1, 1, 1);
        return transactionTemplate.execute(status -> {
            int deleted = holidayMapper.deleteNationalByYear(tenantId, from, to);
            int inserted = holidayMapper.insertNational(tenantId, entries);
            //날짜 중복 허용(#7) — COMPANY와 공존하므로 건너뛰는 항목 없음
            return new HolidaySyncResponse(year, country.name(), entries.size(), inserted, deleted, 0);
        });
    }

    /**
     * 테넌트 생성 시 당해·익년 자동 동기화 — 동기 호출 + 실패 허용(예외 삼킴 + WARN, §2-5).
     *
     * @return 두 해 모두 성공하면 true(false면 W007이 수동 동기화 안내)
     */
    public boolean syncInitialYears(long tenantId) {
        int year = Year.now(clock).getValue();
        try {
            sync(tenantId, year);
            sync(tenantId, year + 1);
            return true;
        } catch (RuntimeException e) {
            log.warn("initial holiday sync failed: tenantId={} — W013 수동 동기화로 수습", tenantId, e);
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> list(long tenantId, int year) {
        if (year < 1 || year > 9998) {
            //LocalDate 표현 범위(1~9999, +1 경계 포함) 밖 — 표시용 조회라 빈 목록(극단값 500 방지)
            return List.of();
        }
        return holidayMapper.findByRange(tenantId, LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1))
                .stream().map(HolidayResponse::from).toList();
    }

    /**
     * 수동 등록 — 항상 COMPANY(요청에 type 없음 — "수동 NATIONAL"이 다음 동기화에서 소리 없이
     * 지워지는 함정을 닫는다). 날짜 중복 허용(#7) — 대리키로 여러 행 공존.
     */
    @Transactional
    public HolidayResponse create(long tenantId, HolidayCreateRequest request) {
        HolidayMapper.HolidayInsert holiday =
                new HolidayMapper.HolidayInsert(tenantId, request.holidayDate(), request.holidayName());
        holidayMapper.insert(holiday);
        return HolidayResponse.from(holidayMapper.findById(tenantId, holiday.getHolidayId()));
    }

    /**
     * §2-3 응답 검증 — 필터(global=true && Public && 국가 일치 && 지역 한정 제외) 후
     * ①1건 이상(빈 응답이 한 해를 지우는 사고 방지) ②전 date가 요청 연도(불일치 1건이라도 전체 중단)
     * ③상한 100건(오염된 대량 응답이 DB를 채우는 사고 방지 — KR/JP 실측 15~20건)
     * ④같은 날짜 중복은 첫 건만(대체 공휴일 등 API의 중복 행 — UNIQUE 충돌로 전체 실패하지 않게).
     */
    private List<NationalHoliday> filterAndValidate(List<NagerHoliday> raw, int year, String countryCode) {
        List<NagerHoliday> filtered = raw.stream()
                .filter(Objects::nonNull)
                .filter(h -> Boolean.TRUE.equals(h.global()))
                .filter(h -> h.counties() == null || h.counties().isEmpty())
                .filter(h -> h.types() != null && h.types().contains("Public"))
                .filter(h -> countryCode.equals(h.countryCode()))
                .toList();
        if (filtered.isEmpty()) {
            //KR/JP에 공휴일 0인 해는 없다 — 빈 응답은 데이터 이상(성공 처리 금지)
            throw RestNagerDateClient.upstreamFailure();
        }
        if (filtered.size() > MAX_HOLIDAYS_PER_YEAR) {
            throw RestNagerDateClient.upstreamFailure();
        }
        Map<LocalDate, NationalHoliday> byDate = new LinkedHashMap<>();
        for (NagerHoliday h : filtered) {
            LocalDate date;
            try {
                date = LocalDate.parse(h.date());
            } catch (RuntimeException e) {
                throw RestNagerDateClient.upstreamFailure();
            }
            if (date.getYear() != year) {
                throw RestNagerDateClient.upstreamFailure();
            }
            String name = h.localName() == null ? "" : h.localName();
            if (name.isBlank()) {
                throw RestNagerDateClient.upstreamFailure();
            }
            if (name.length() > NAME_MAX_LENGTH) {
                name = name.substring(0, NAME_MAX_LENGTH);
            }
            byDate.putIfAbsent(date, new NationalHoliday(date, name));
        }
        return List.copyOf(byDate.values());
    }

    /** 허용 연도 = 현재 −1 ~ +2 — 원거리 연도 남발로 외부 API를 두드리는 것을 막는 안전핀. */
    private void validateYearRange(int year) {
        int current = Year.now(clock).getValue();
        if (year < current - 1 || year > current + 2) {
            throw ApiException.badRequest("HOLIDAY_YEAR_RANGE", "holiday.year.range");
        }
    }

    private Tenant requireTenant(long tenantId) {
        Tenant tenant = tenantMapper.findById(tenantId);
        if (tenant == null) {
            throw ApiException.notFound("TENANT_NOT_FOUND", "tenant.not-found");
        }
        return tenant;
    }

    /** country null/미지원이면 KR로 동작(안전한 실패 — 기존 행 전부 KR backfill이라 실측 도달 불가). */
    private ProfileCountry countryOf(Tenant tenant) {
        ProfileCountry country = ProfileCountry.of(tenant.country());
        return country == null ? ProfileCountry.KR : country;
    }

}
