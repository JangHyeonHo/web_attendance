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
            when(holidayMapper.insertNational(eq(TENANT_ID), anyList())).thenReturn(1);

            HolidaySyncResponse response = service().sync(TENANT_ID, 2026);

            assertThat(response.fetched()).isEqualTo(1);
            assertThat(response.inserted()).isEqualTo(1);
            verify(holidayMapper).insertNational(eq(TENANT_ID), eq(List.of(
                    new HolidayMapper.NationalHoliday(LocalDate.of(2026, 3, 1), "삼일절"))));
        }

        @Test
        @DisplayName("같은 날짜 중복 응답은 첫 건만 반영(대체공휴일 중복 행이 UNIQUE 충돌로 전체를 깨지 않게)")
        void duplicateDatesKeepFirst() {
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
            when(nagerDateClient.fetch(2026, "KR")).thenReturn(List.of(
                    publicHoliday("2026-03-01", "삼일절", "KR"),
                    publicHoliday("2026-03-01", "삼일절 대체", "KR"),
                    publicHoliday("2026-05-05", "어린이날", "KR")));
            when(holidayMapper.deleteNationalByYear(eq(TENANT_ID), any(), any())).thenReturn(0);
            when(holidayMapper.insertNational(eq(TENANT_ID), anyList())).thenReturn(2);

            service().sync(TENANT_ID, 2026);

            verify(holidayMapper).insertNational(eq(TENANT_ID), eq(List.of(
                    new HolidayMapper.NationalHoliday(LocalDate.of(2026, 3, 1), "삼일절"),
                    new HolidayMapper.NationalHoliday(LocalDate.of(2026, 5, 5), "어린이날"))));
        }

        @Test
        @DisplayName("연간 100건 초과 응답은 502(오염된 대량 응답 방어) — DB 무변경")
        void oversizedResponseRejected() {
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
            List<NagerHoliday> flood = new java.util.ArrayList<>();
            for (int i = 0; i < 101; i++) {
                flood.add(publicHoliday(LocalDate.of(2026, 1, 1).plusDays(i).toString(), "휴일" + i, "KR"));
            }
            when(nagerDateClient.fetch(2026, "KR")).thenReturn(flood);

            expectUpstream(() -> service().sync(TENANT_ID, 2026));
            verify(holidayMapper, never()).deleteNationalByYear(anyLong(), any(), any());
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
            verify(holidayMapper, never()).insertNational(anyLong(), anyList());
        }

        @Test
        @DisplayName("HOL-04: 삭제는 해당 연도 NATIONAL만(인자 검증), 날짜 중복 허용 — 전량 삽입(skippedCompany=0)")
        void deleteScopeAllInserted() {
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("KR"));
            when(nagerDateClient.fetch(2026, "KR")).thenReturn(List.of(
                    publicHoliday("2026-03-01", "삼일절", "KR"),
                    publicHoliday("2026-10-01", "창립기념일과 겹침", "KR")));
            when(holidayMapper.deleteNationalByYear(TENANT_ID,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))).thenReturn(3);
            //날짜 중복 허용 — COMPANY와 공존, 2건 모두 삽입
            when(holidayMapper.insertNational(eq(TENANT_ID), anyList())).thenReturn(2);

            HolidaySyncResponse response = service().sync(TENANT_ID, 2026);

            verify(holidayMapper).deleteNationalByYear(TENANT_ID,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
            assertThat(response.fetched()).isEqualTo(2);
            assertThat(response.inserted()).isEqualTo(2);
            assertThat(response.deleted()).isEqualTo(3);
            assertThat(response.skippedCompany()).isEqualTo(0);
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
            when(holidayMapper.insertNational(anyLong(), anyList())).thenReturn(1);
            assertThat(service.sync(TENANT_ID, 2025).year()).isEqualTo(2025);
            assertThat(service.sync(TENANT_ID, 2028).year()).isEqualTo(2028);
        }

        @Test
        @DisplayName("소재국이 동기화 국가를 결정한다(JP 테넌트 → JP 요청)")
        void countryFromTenant() {
            when(tenantMapper.findById(TENANT_ID)).thenReturn(tenant("JP"));
            when(nagerDateClient.fetch(2026, "JP"))
                    .thenReturn(List.of(publicHoliday("2026-01-01", "元日", "JP")));
            when(holidayMapper.insertNational(anyLong(), anyList())).thenReturn(1);

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
            when(holidayMapper.insertNational(anyLong(), anyList())).thenReturn(1);

            assertThat(service().syncInitialYears(TENANT_ID)).isTrue();

            verify(nagerDateClient).fetch(2026, "KR");
            verify(nagerDateClient).fetch(2027, "KR");
        }
    }

    @Nested
    @DisplayName("CRUD(HOL-06)")
    class Crud {

        @Test
        @DisplayName("수동 등록은 항상 COMPANY로 저장, 날짜 중복 허용(대리키 회수)")
        void createAlwaysCompany() {
            LocalDate date = LocalDate.of(2026, 10, 1);
            //insert가 생성키를 holidayId에 채운다 — findById로 응답 회수
            when(holidayMapper.insert(any(HolidayMapper.HolidayInsert.class))).thenAnswer(inv -> {
                inv.getArgument(0, HolidayMapper.HolidayInsert.class).setHolidayId(42L);
                return 1;
            });
            when(holidayMapper.findById(TENANT_ID, 42L)).thenReturn(new Holiday(
                    42L, TENANT_ID, date, "창립기념일", HolidayType.COMPANY, LocalDateTime.now(), LocalDateTime.now()));

            var response = service().create(TENANT_ID, new HolidayCreateRequest(date, "창립기념일"));
            assertThat(response.holidayType()).isEqualTo(HolidayType.COMPANY);
            assertThat(response.holidayId()).isEqualTo(42L);
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

        @Test
        @DisplayName("LocalDate 표현 범위 밖 연도 조회는 빈 목록(극단값 500 방지) — 매퍼 미호출")
        void listExtremeYearReturnsEmpty() {
            assertThat(service().list(TENANT_ID, Integer.MAX_VALUE)).isEmpty();
            assertThat(service().list(TENANT_ID, 0)).isEmpty();
            assertThat(service().list(TENANT_ID, 9999)).isEmpty();
            verify(holidayMapper, never()).findByRange(anyLong(), any(), any());
        }
    }

}
