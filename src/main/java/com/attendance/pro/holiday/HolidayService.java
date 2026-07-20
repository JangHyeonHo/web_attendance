package com.attendance.pro.holiday;

import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
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
            //이 연도가 (재)동기화되는 시점에 반복 지정 회사 공휴일도 채운다(#8) —
            //같은 명칭이 이미 있으면 생략(멱등). 신규 연도 생성 시 자동 편입되는 경로.
            materializeRecurringInto(tenantId, year);
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
     * 반복(recurring) 지정 시 이미 동기화된 모든 연도로 인스턴스를 실체화한다(#8).
     */
    @Transactional
    public HolidayResponse create(long tenantId, HolidayCreateRequest request) {
        boolean recurring = request.recurringFlag();
        HolidayMapper.HolidayInsert holiday = new HolidayMapper.HolidayInsert(
                tenantId, request.holidayDate(), request.holidayName(), recurring);
        holidayMapper.insert(holiday);
        if (recurring) {
            spreadRecurringToAllYears(tenantId, request.holidayDate(), request.holidayName());
        }
        return HolidayResponse.from(holidayMapper.findById(tenantId, holiday.getHolidayId()));
    }

    /**
     * 회사 공휴일(COMPANY) 개별 수정(#8) — 날짜/명칭 이동·반복 토글. 국가 공휴일/미존재면 404
     * (매퍼가 type='COMPANY'로 한정). 각 연도 인스턴스는 독립 행이라 이 수정은 해당 연도만 바꾼다.
     * 반복을 새로 켜면 다른 동기화 연도로 인스턴스를 채운다(끄는 것은 소급 삭제하지 않음).
     */
    @Transactional
    public HolidayResponse updateCompany(long tenantId, long holidayId, HolidayDtos.HolidayUpdateRequest request) {
        boolean recurring = request.recurringFlag();
        int updated = holidayMapper.updateCompany(
                tenantId, holidayId, request.holidayDate(), request.holidayName(), recurring);
        if (updated == 0) {
            throw ApiException.notFound("HOLIDAY_NOT_FOUND", "holiday.not-found");
        }
        if (recurring) {
            spreadRecurringToAllYears(tenantId, request.holidayDate(), request.holidayName());
        }
        return HolidayResponse.from(holidayMapper.findById(tenantId, holidayId));
    }

    /**
     * 반복 공휴일을 동기화된 모든 연도로 실체화 — 기준 행(seedDate)의 월-일을 각 연도에 적용.
     * 같은 명칭이 이미 있는 연도는 생략(멱등). 기준 연도 자신은 이미 행이 있으므로 건너뛴다.
     * (반복 지정/생성 시점 호출 — "이미 있으면 전부 추가" 요구.)
     */
    private void spreadRecurringToAllYears(long tenantId, LocalDate seedDate, String name) {
        MonthDay md = MonthDay.from(seedDate);
        for (int year : holidayMapper.syncedYears(tenantId)) {
            if (year == seedDate.getYear()) {
                continue;
            }
            insertRecurringInstance(tenantId, year, md, name);
        }
    }

    /**
     * 특정 연도에 반복 지정 회사 공휴일 인스턴스를 채운다(#8) — 명칭별 최신 정의(월-일)를 사용,
     * 그 연도에 같은 명칭이 이미 있으면 생략(멱등). sync가 연도 (재)생성 때 호출.
     */
    private void materializeRecurringInto(long tenantId, int year) {
        List<Holiday> recurring = holidayMapper.findRecurringCompany(tenantId);
        //날짜 내림차순 → 명칭별 첫 등장이 최신 정의. 명칭 중복 제거로 연 1건만 실체화.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Holiday h : recurring) {
            if (!seen.add(h.holidayName())) {
                continue;
            }
            insertRecurringInstance(tenantId, year, MonthDay.from(h.holidayDate()), h.holidayName());
        }
    }

    /** 한 (연도, 월-일, 명칭) 인스턴스 삽입 — 같은 명칭이 그 해에 이미 있으면 생략. 2/29는 비윤년 2/28로. */
    private void insertRecurringInstance(long tenantId, int year, MonthDay md, String name) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year + 1, 1, 1);
        if (holidayMapper.countByNameInYear(tenantId, name, from, to) > 0) {
            return;
        }
        //MonthDay.atYear는 비윤년의 2/29를 2/28로 조정한다(예외 없음)
        holidayMapper.insert(new HolidayMapper.HolidayInsert(tenantId, md.atYear(year), name, true));
    }

    /**
     * 회사 공휴일 삭제(#7) — COMPANY만 삭제 가능. 국가 공휴일(NATIONAL)이거나 미존재면 404
     * (매퍼가 type='COMPANY'로 한정하므로 국가 공휴일 id는 매칭되지 않는다 = 삭제 불가 보장).
     */
    @Transactional
    public void deleteCompany(long tenantId, long holidayId) {
        if (holidayMapper.deleteCompanyById(tenantId, holidayId) == 0) {
            throw ApiException.notFound("HOLIDAY_NOT_FOUND", "holiday.not-found");
        }
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
