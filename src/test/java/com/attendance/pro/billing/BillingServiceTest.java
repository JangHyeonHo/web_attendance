package com.attendance.pro.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.billing.BillingDtos.InvoiceResponse;
import com.attendance.pro.billing.BillingDtos.InvoiceStatus;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.tenant.TenantBilling;
import com.attendance.pro.tenant.TenantBillingMapper;
import com.attendance.pro.tenant.TenantDtos.BillingMethod;

/**
 * 등록 시점 일할계산(seat-day proration) 단위 테스트.
 * 좌석일 = 기초좌석×월일수 + Σ_증원(Δ과금좌석 × (월일수−등록일)), 공급가 = round(좌석일 × 단가 ÷ 월일수).
 * 증원은 등록일 다음 날부터(당일 제외), 감원은 당월 미반영(다음 달부터).
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    private static final long TENANT = 10L;
    private static final String YM = "2026-07";       //고정 시계의 현재 달(31일)
    private static final LocalDate FIRST = LocalDate.of(2026, 7, 1);
    private static final int D = 31;                   //2026-07 일수

    @Mock
    private TenantBillingMapper tenantBillingMapper;
    @Mock
    private SeatEventMapper seatEventMapper;
    @Mock
    private InvoiceMapper invoiceMapper;

    /** 현재 달을 2026-07로 고정 */
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneId.of("UTC"));

    private BillingService service() {
        return new BillingService(tenantBillingMapper, seatEventMapper, invoiceMapper, clock);
    }

    private static TenantBilling config(int perSeat, int freeSeats) {
        return new TenantBilling(TENANT, BillingMethod.INVOICE, "b@acme.co.kr", null, null, null,
                "BASIC", perSeat, freeSeats, null, null, LocalDateTime.now(), LocalDateTime.now());
    }

    /** 그 달 이벤트 없음 + 이월 활성 좌석 = startActive (기초좌석으로 전액 과금). */
    private void carried(int startActive) {
        when(seatEventMapper.activeBefore(TENANT, FIRST)).thenReturn(startActive);
    }

    private SeatEvent ev(int day, int activeSeats) {
        return new SeatEvent(LocalDate.of(2026, 7, day), activeSeats);
    }

    @Test
    @DisplayName("BILL-01: 이월 좌석은 전액(무료 초과분 × 전일수) + VAT 별도(잠정)")
    void carriedFullMonth() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        carried(8);                                    //과금좌석 3, 전월 이월

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.status()).isEqualTo(InvoiceStatus.PROVISIONAL);
        assertThat(r.maxSeats()).isEqualTo(8);
        assertThat(r.billedSeats()).isEqualTo(3);       //8 - 5
        assertThat(r.daysInMonth()).isEqualTo(D);
        assertThat(r.seatDays()).isEqualTo(3L * D);      //93
        assertThat(r.subtotal()).isEqualTo(6000);        //round(93 × 2000 / 31)
        assertThat(r.vat()).isEqualTo(600);
        assertThat(r.total()).isEqualTo(6600);
        assertThat(r.issuedAt()).isNull();
    }

    @Test
    @DisplayName("BILL-02: 무료 인원 이하는 과금 0")
    void underFreeIsZero() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        carried(5);

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.billedSeats()).isZero();
        assertThat(r.seatDays()).isZero();
        assertThat(r.total()).isZero();
    }

    @Test
    @DisplayName("BILL-03: 결제 설정이 없으면 기본값(단가 2000·무료 5) 적용")
    void defaultsWhenNoConfig() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(null);
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        carried(6);

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.freeSeats()).isEqualTo(5);
        assertThat(r.unitPrice()).isEqualTo(2000);
        assertThat(r.billedSeats()).isEqualTo(1);        //6 - 5
        assertThat(r.total()).isEqualTo(2200);           //2000 + 200
    }

    @Test
    @DisplayName("BILL-04: 부가세는 반올림한다(공급가×10%)")
    void vatRoundsHalfUp() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(1999, 0));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        carried(1);

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.subtotal()).isEqualTo(1999);        //round(31 × 1999 / 31)
        assertThat(r.vat()).isEqualTo(200);              //199.9 → 200
        assertThat(r.total()).isEqualTo(2199);
    }

    @Test
    @DisplayName("BILL-05: 이력이 전혀 없으면 0인으로 계산")
    void noHistoryIsZeroSeats() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        //activeBefore 미스텁 → null(=0), inMonth 미스텁 → 빈 리스트

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.maxSeats()).isZero();
        assertThat(r.total()).isZero();
    }

    @Test
    @DisplayName("BILL-06: 마감된 달은 스냅샷(확정)을 그대로 반환 — 현재 단가 변경과 무관")
    void issuedReturnsSnapshot() {
        Invoice snap = new Invoice(TENANT, YM, 10, 5, 5, 155, 31, 3000, 15000, 1500, 16500,
                LocalDateTime.of(2026, 8, 1, 0, 0));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(snap);

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(r.unitPrice()).isEqualTo(3000);       //스냅샷 단가(현재 config 미조회)
        assertThat(r.seatDays()).isEqualTo(155);
        assertThat(r.total()).isEqualTo(16500);
        assertThat(r.issuedAt()).isNotNull();
    }

    @Test
    @DisplayName("BILL-07: 마감은 좌석일 계산값을 invoice에 스냅샷하고 확정으로 반환")
    void closeSnapshots() {
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null,
                new Invoice(TENANT, YM, 8, 5, 3, 93, 31, 2000, 6000, 600, 6600,
                        LocalDateTime.of(2026, 8, 1, 0, 0)));
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        carried(8);

        InvoiceResponse r = service().close(TENANT, YM);

        //maxSeats=8, free=5, billed=3, seatDays=93, days=31, unit=2000, subtotal=6000, vat=600, total=6600
        verify(invoiceMapper).insert(TENANT, YM, 8, 5, 3, 93L, 31, 2000, 6000L, 600L, 6600L);
        assertThat(r.status()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    @DisplayName("BILL-08: 이미 마감된 달 재마감은 409")
    void closeAlreadyIssuedConflicts() {
        when(invoiceMapper.find(TENANT, YM)).thenReturn(
                new Invoice(TENANT, YM, 8, 5, 3, 93, 31, 2000, 6000, 600, 6600,
                        LocalDateTime.of(2026, 8, 1, 0, 0)));

        assertThatThrownBy(() -> service().close(TENANT, YM))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(409));
        verify(invoiceMapper, never()).insert(anyLong(), anyString(),
                anyInt(), anyInt(), anyInt(), anyLong(), anyInt(), anyInt(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("BILL-09: 청구월 형식이 틀리면 400")
    void invalidYmRejected() {
        assertThatThrownBy(() -> service().getInvoice(TENANT, "2026/07"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    @DisplayName("BILL-10: 공급가가 int 범위를 넘어도 long으로 정확히 계산")
    void largeAmountDoesNotOverflow() {
        //단가 1천만 × 1000명(전월 이월, 전액) = 100억 > Integer.MAX_VALUE
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(10_000_000, 0));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        carried(1000);

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.seatDays()).isEqualTo(1000L * D);   //31000
        assertThat(r.subtotal()).isEqualTo(10_000_000_000L);
        assertThat(r.vat()).isEqualTo(1_000_000_000L);
        assertThat(r.total()).isEqualTo(11_000_000_000L);
    }

    @Test
    @DisplayName("PRORATE-01: 월말(마지막날) 신규 등록은 당월 0원(당일 제외 → 잔여일 0)")
    void monthEndRegistrationIsZero() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        //이월 0, 마지막 날(31일)에 20명 등록
        when(seatEventMapper.inMonth(TENANT, FIRST, LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(ev(31, 20)));

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.maxSeats()).isEqualTo(20);          //표시용 피크
        assertThat(r.billedSeats()).isEqualTo(15);       //기준선은 올라감(다음 달 전액 대비)
        assertThat(r.seatDays()).isZero();               //15 × (31-31) = 0
        assertThat(r.total()).isZero();
    }

    @Test
    @DisplayName("PRORATE-02: 월초(1일) 증원은 다음 날부터 일할(잔여 30일) 누적")
    void midMonthIncreaseAccruesFromNextDay() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        //이월 5명(과금 0) → 1일에 6명(과금 1) 증원
        when(seatEventMapper.activeBefore(TENANT, FIRST)).thenReturn(5);
        when(seatEventMapper.inMonth(TENANT, FIRST, LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(ev(1, 6)));

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.billedSeats()).isEqualTo(1);
        assertThat(r.seatDays()).isEqualTo(30);          //base 0×31 + 1 × (31-1)
    }

    @Test
    @DisplayName("PRORATE-03: 감원은 당월 미반영 — 이월 기준선 전액 유지(다음 달부터 감소)")
    void downgradeDoesNotReduceCurrentMonth() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        //이월 20명(과금 15) → 10일에 12명으로 감원
        when(seatEventMapper.activeBefore(TENANT, FIRST)).thenReturn(20);
        when(seatEventMapper.inMonth(TENANT, FIRST, LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(ev(10, 12)));

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.maxSeats()).isEqualTo(20);          //피크(감원 전)
        assertThat(r.billedSeats()).isEqualTo(15);       //기준선 안 내려감
        assertThat(r.seatDays()).isEqualTo(15L * D);     //465 — 전액
        assertThat(r.total()).isEqualTo(33000);          //round(465 × 2000/31)=30000 +VAT
    }

    @Test
    @DisplayName("PRORATE-04: 감원 후 재증원은 기준선 초과분만 그 등록일 다음 날부터 누적")
    void reIncreaseAbovePeakAccrues() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        //이월 10명(과금 5) → 5일 8명(과금 3, 감원=미반영) → 25일 13명(과금 8, 기준선 5→8)
        when(seatEventMapper.activeBefore(TENANT, FIRST)).thenReturn(10);
        when(seatEventMapper.inMonth(TENANT, FIRST, LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(ev(5, 8), ev(25, 13)));

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.maxSeats()).isEqualTo(13);
        assertThat(r.billedSeats()).isEqualTo(8);
        //base 5×31=155 + (8-5) × (31-25)=18 → 173
        assertThat(r.seatDays()).isEqualTo(173);
    }
}
