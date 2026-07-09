package com.attendance.pro.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.holiday.HolidayDtos.HolidayCreateRequest;
import com.attendance.pro.holiday.HolidayDtos.HolidaySyncResponse;
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.tenant.TenantStatus;

/**
 * 공휴일 동기화·CRUD 테스트 — HOL-02~07(Nager는 Mockito 목, 실 외부 API 미호출).
 */
@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    private static final long TENANT_ID = 10L;
    /** 고정 현재 연도: 2026 */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-09T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private HolidayMapper holidayMapper;
    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private NagerDateClient nagerDateClient;

    private HolidayService service() {
        return new HolidayService(holidayMapper, tenantMapper, nagerDateClient, immediateTx(), FIXED_CLOCK);
    }

    private static TransactionTemplate immediateTx() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }

    private static Tenant tenant(String country) {
        return new Tenant(TENANT_ID, "ACME", "에이크미(주)", country, TenantStatus.ACTIVE, LocalDateTime.now());
    }

    private static NagerHoliday nager(String date, String localName, String countryCode,
            Boolean global, List<String> counties, List<String> types) {
        return new NagerHoliday(date, localName, localName + "(en)", countryCode, global, counties, types);
    }

    private static NagerHoliday publicHoliday(String date, String localName, String countryCode) {
        return nager(date, localName, countryCode, true, null, List.of("Public"));
    }

    private void expectUpstream(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(502);
                    assertThat(apiException.getCode()).isEqualTo("HOLIDAY_SYNC_UPSTREAM");
                });
    }

    @Nested
    @DisplayName("동기화(sync)")
    class Sync {

        @Test
        @DisplayName("HOL-02: global=false·지역 한정(counties)·Public 아님·국가 불일치 항목은 제외된다")
        void filtersNonEligibleEntries() {
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
            when(nagerDateClient.fetch(2026, "KR")).thenReturn(List.of(
                    publicHoliday("2026-03-01", "삼일절", "KR"),                                   //채용
                    nager("2026-03-02", "지역휴일", "KR", false, null, List.of("Public")),          //global=false 제외
                    nager("2026-03-03", "현한정", "KR", true, List.of("KR-11"), List.of("Public")), //지역 한정 제외
                    nager("2026-03-04", "기념일", "KR", true, null, List.of("Observance")),         //Public 아님 제외
                    publicHoliday("2026-03-05", "타국", "JP")));                                    //국가 불일치 제외
            when(holidayMapper.deleteNationalByYear(eq(TENANT_ID), any(), any())).thenReturn(0);
            when(holidayMapper.insertNationalIgnore(eq(TENANT_ID), anyList())).thenReturn(1);

            HolidaySyncResponse response = service().sync(TENANT_ID, 2026);

            assertThat(response.fetched()).isEqualTo(1);
            assertThat(response.inserted()).isEqualTo(1);
            verify(holidayMapper).insertNationalIgnore(eq(TENANT_ID), eq(List.of(
                    new HolidayMapper.NationalHoliday(LocalDate.of(2026, 3, 1), "삼일절"))));
        }

        @Test
        @DisplayName("HOL-03: 빈 목록/연도 불일치/파싱 불가는 502 + 매퍼 delete/insert 미호출(DB 무변경)")
        void validationFailureLeavesDbUntouched() {
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
            HolidayService service = service();
            //빈 응답(필터 후 0건) — 성공 처리하면 삭제 단계가 한 해를 지우는 사고가 된다
            when(nagerDateClient.fetch(2026, "KR")).thenReturn(List.of());
            expectUpstream(() -> service.sync(TENANT_ID, 2026));
            //연도 불일치
            when(nagerDateClient.fetch(2026, "KR"))
                    .thenReturn(List.of(publicHoliday("2025-03-01", "삼일절", "KR")));
            expectUpstream(() -> service.sync(TENANT_ID, 2026));
            //파싱 불가
            when(nagerDateClient.fetch(2026, "KR"))
                    .thenReturn(List.of(publicHoliday("not-a-date", "삼일절", "KR")));
            expectUpstream(() -> service.sync(TENANT_ID, 2026));

            verify(holidayMapper, never()).deleteNationalByYear(anyLong(), any(), any());
            verify(holidayMapper, never()).insertNationalIgnore(anyLong(), anyList());
        }

        @Test
        @DisplayName("HOL-04: 삭제는 해당 연도 NATIONAL만(인자 검증), COMPANY 겹침은 skippedCompany로 카운트")
        void deleteScopeAndCompanySkip() {
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
            when(nagerDateClient.fetch(2026, "KR")).thenReturn(List.of(
                    publicHoliday("2026-03-01", "삼일절", "KR"),
                    publicHoliday("2026-10-01", "창립기념일과 겹침", "KR")));
            when(holidayMapper.deleteNationalByYear(TENANT_ID,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))).thenReturn(3);
            //2건 중 1건은 COMPANY 기존 행에 막혀 IGNORE(삽입 1건)
            when(holidayMapper.insertNationalIgnore(eq(TENANT_ID), anyList())).thenReturn(1);

            HolidaySyncResponse response = service().sync(TENANT_ID, 2026);

            verify(holidayMapper).deleteNationalByYear(TENANT_ID,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
            assertThat(response.fetched()).isEqualTo(2);
            assertThat(response.inserted()).isEqualTo(1);
            assertThat(response.deleted()).isEqualTo(3);
            assertThat(response.skippedCompany()).isEqualTo(1);
        }

        @Test
        @DisplayName("HOL-05: 연도 범위 밖 400 HOLIDAY_YEAR_RANGE — 경계 -1/+2는 통과")
        void yearRangeGuard() {
            HolidayService service = service();
            //범위 밖(외부 호출 이전에 차단)
            assertThatThrownBy(() -> service.sync(TENANT_ID, 2024))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getStatus().value()).isEqualTo(400);
                        assertThat(apiException.getCode()).isEqualTo("HOLIDAY_YEAR_RANGE");
                    });
            assertThatThrownBy(() -> service.sync(TENANT_ID, 2029))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("HOLIDAY_YEAR_RANGE"));
            //경계 통과: 2025(-1), 2028(+2)
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
            when(nagerDateClient.fetch(2025, "KR"))
                    .thenReturn(List.of(publicHoliday("2025-03-01", "삼일절", "KR")));
            when(nagerDateClient.fetch(2028, "KR"))
                    .thenReturn(List.of(publicHoliday("2028-03-01", "삼일절", "KR")));
            when(holidayMapper.insertNationalIgnore(anyLong(), anyList())).thenReturn(1);
            assertThat(service.sync(TENANT_ID, 2025).year()).isEqualTo(2025);
            assertThat(service.sync(TENANT_ID, 2028).year()).isEqualTo(2028);
        }

        @Test
        @DisplayName("소재국이 동기화 국가를 결정한다(JP 테넌트 → JP 요청)")
        void countryFromTenant() {
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("JP"));
            when(nagerDateClient.fetch(2026, "JP"))
                    .thenReturn(List.of(publicHoliday("2026-01-01", "元日", "JP")));
            when(holidayMapper.insertNationalIgnore(anyLong(), anyList())).thenReturn(1);

            HolidaySyncResponse response = service().sync(TENANT_ID, 2026);

            assertThat(response.country()).isEqualTo("JP");
            verify(nagerDateClient).fetch(2026, "JP");
        }

        @Test
        @DisplayName("초기 동기(당해·익년): 실패 시 예외를 삼키고 false(생성은 성공 유지 — HOL-07 연계)")
        void initialSyncSwallowsFailure() {
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
            when(nagerDateClient.fetch(anyInt(), anyString()))
                    .thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                            "HOLIDAY_SYNC_UPSTREAM", "holiday.sync.upstream"));

            assertThat(service().syncInitialYears(TENANT_ID)).isFalse();
        }

        @Test
        @DisplayName("초기 동기 성공: 당해·익년 2회 sync 후 true")
        void initialSyncBothYears() {
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
            when(nagerDateClient.fetch(2026, "KR"))
                    .thenReturn(List.of(publicHoliday("2026-03-01", "삼일절", "KR")));
            when(nagerDateClient.fetch(2027, "KR"))
                    .thenReturn(List.of(publicHoliday("2027-03-01", "삼일절", "KR")));
            when(holidayMapper.insertNationalIgnore(anyLong(), anyList())).thenReturn(1);

            assertThat(service().syncInitialYears(TENANT_ID)).isTrue();

            verify(nagerDateClient).fetch(2026, "KR");
            verify(nagerDateClient).fetch(2027, "KR");
        }
    }

    @Nested
    @DisplayName("CRUD(HOL-06)")
    class Crud {

        @Test
        @DisplayName("수동 등록은 항상 COMPANY로 저장, 중복은 409 HOLIDAY_DATE_DUPLICATED")
        void createAlwaysCompanyAndDuplicate409() {
            LocalDate date = LocalDate.of(2026, 10, 1);
            when(holidayMapper.insert(TENANT_ID, date, "창립기념일")).thenReturn(1);
            when(holidayMapper.findByDate(TENANT_ID, date)).thenReturn(new Holiday(
                    TENANT_ID, date, "창립기념일", HolidayType.COMPANY, LocalDateTime.now(), LocalDateTime.now()));

            var response = service().create(TENANT_ID, new HolidayCreateRequest(date, "창립기념일"));
            assertThat(response.holidayType()).isEqualTo(HolidayType.COMPANY);

            when(holidayMapper.insert(TENANT_ID, date, "창립기념일"))
                    .thenThrow(new DuplicateKeyException("dup"));
            assertThatThrownBy(() -> service().create(TENANT_ID, new HolidayCreateRequest(date, "창립기념일")))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getStatus().value()).isEqualTo(409);
                        assertThat(apiException.getCode()).isEqualTo("HOLIDAY_DATE_DUPLICATED");
                    });
        }

        @Test
        @DisplayName("미존재 날짜의 수정/삭제는 404 HOLIDAY_NOT_FOUND")
        void updateDeleteNotFound() {
            LocalDate date = LocalDate.of(2026, 10, 1);
            when(holidayMapper.updateName(TENANT_ID, date, "새 이름")).thenReturn(0);
            assertThatThrownBy(() -> service().updateName(TENANT_ID, date, "새 이름"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("HOLIDAY_NOT_FOUND"));

            when(holidayMapper.deleteByDate(TENANT_ID, date)).thenReturn(0);
            assertThatThrownBy(() -> service().delete(TENANT_ID, date))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("HOLIDAY_NOT_FOUND"));
        }

        @Test
        @DisplayName("PUT은 명칭만 수정(유형 불변 — updateName 매퍼로만 위임)")
        void updateNameOnly() {
            LocalDate date = LocalDate.of(2026, 3, 1);
            when(holidayMapper.updateName(TENANT_ID, date, "삼일절(수정)")).thenReturn(1);
            when(holidayMapper.findByDate(TENANT_ID, date)).thenReturn(new Holiday(
                    TENANT_ID, date, "삼일절(수정)", HolidayType.NATIONAL, LocalDateTime.now(), LocalDateTime.now()));

            var response = service().updateName(TENANT_ID, date, "삼일절(수정)");

            assertThat(response.holidayName()).isEqualTo("삼일절(수정)");
            assertThat(response.holidayType()).isEqualTo(HolidayType.NATIONAL);
        }

        @Test
        @DisplayName("목록은 연도 범위로 조회한다(tenantId 전파)")
        void listByYear() {
            when(holidayMapper.findByRange(TENANT_ID,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))).thenReturn(List.of());

            assertThat(service().list(TENANT_ID, 2026)).isEmpty();

            verify(holidayMapper).findByRange(TENANT_ID,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        }
    }

}
