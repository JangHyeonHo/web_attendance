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
import java.time.LocalDateTime;
import java.time.ZoneId;

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
 * 인당 과금 계산·마감 단위 테스트.
 * 과금인원 = max(0, 월중최대 - 무료), 공급가 = 과금인원×단가, 부가세 = round(공급가×10%), 합계 = 공급가+부가세.
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    private static final long TENANT = 10L;
    private static final String YM = "2026-07"; //고정 시계의 현재 달

    @Mock
    private TenantBillingMapper tenantBillingMapper;
    @Mock
    private SeatUsageMapper seatUsageMapper;
    @Mock
    private InvoiceMapper invoiceMapper;

    /** 현재 달을 2026-07로 고정 */
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneId.of("UTC"));

    private BillingService service() {
        return new BillingService(tenantBillingMapper, seatUsageMapper, invoiceMapper, clock);
    }

    private static TenantBilling config(int perSeat, int freeSeats) {
        return new TenantBilling(TENANT, BillingMethod.INVOICE, "b@acme.co.kr", null, null, null,
                "BASIC", perSeat, freeSeats, null, null, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("BILL-01: 무료 5인 초과분에만 과금 + VAT 별도(잠정)")
    void provisionalAboveFree() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        when(seatUsageMapper.findMaxSeats(TENANT, YM)).thenReturn(8);

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.status()).isEqualTo(InvoiceStatus.PROVISIONAL);
        assertThat(r.maxSeats()).isEqualTo(8);
        assertThat(r.billedSeats()).isEqualTo(3);      //8 - 5
        assertThat(r.subtotal()).isEqualTo(6000);      //3 × 2000
        assertThat(r.vat()).isEqualTo(600);            //10%
        assertThat(r.total()).isEqualTo(6600);
        assertThat(r.issuedAt()).isNull();
    }

    @Test
    @DisplayName("BILL-02: 무료 인원 이하는 과금 0")
    void underFreeIsZero() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        when(seatUsageMapper.findMaxSeats(TENANT, YM)).thenReturn(5);

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.billedSeats()).isZero();
        assertThat(r.subtotal()).isZero();
        assertThat(r.vat()).isZero();
        assertThat(r.total()).isZero();
    }

    @Test
    @DisplayName("BILL-03: 결제 설정이 없으면 기본값(단가 2000·무료 5) 적용")
    void defaultsWhenNoConfig() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(null);
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        when(seatUsageMapper.findMaxSeats(TENANT, YM)).thenReturn(6);

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.freeSeats()).isEqualTo(5);
        assertThat(r.unitPrice()).isEqualTo(2000);
        assertThat(r.billedSeats()).isEqualTo(1);      //6 - 5
        assertThat(r.total()).isEqualTo(2200);         //2000 + 200
    }

    @Test
    @DisplayName("BILL-04: 부가세는 반올림한다(공급가×10%)")
    void vatRoundsHalfUp() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(1999, 0));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        when(seatUsageMapper.findMaxSeats(TENANT, YM)).thenReturn(1);

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.subtotal()).isEqualTo(1999);
        assertThat(r.vat()).isEqualTo(200);            //199.9 → 200
        assertThat(r.total()).isEqualTo(2199);
    }

    @Test
    @DisplayName("BILL-05: 기록된 사용량이 없으면 0인으로 계산")
    void noUsageIsZeroSeats() {
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null);
        when(seatUsageMapper.findMaxSeats(TENANT, YM)).thenReturn(null);

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.maxSeats()).isZero();
        assertThat(r.total()).isZero();
    }

    @Test
    @DisplayName("BILL-06: 마감된 달은 스냅샷(확정)을 그대로 반환 — 현재 단가 변경과 무관")
    void issuedReturnsSnapshot() {
        Invoice snap = new Invoice(TENANT, YM, 10, 5, 5, 3000, 15000, 1500, 16500,
                LocalDateTime.of(2026, 8, 1, 0, 0));
        when(invoiceMapper.find(TENANT, YM)).thenReturn(snap);

        InvoiceResponse r = service().getInvoice(TENANT, YM);

        assertThat(r.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(r.unitPrice()).isEqualTo(3000);     //스냅샷 단가(현재 config 미조회)
        assertThat(r.total()).isEqualTo(16500);
        assertThat(r.issuedAt()).isNotNull();
    }

    @Test
    @DisplayName("BILL-07: 마감은 계산값을 invoice에 스냅샷하고 확정으로 반환")
    void closeSnapshots() {
        when(invoiceMapper.find(TENANT, YM)).thenReturn(null,
                new Invoice(TENANT, YM, 8, 5, 3, 2000, 6000, 600, 6600, LocalDateTime.of(2026, 8, 1, 0, 0)));
        when(tenantBillingMapper.findById(TENANT)).thenReturn(config(2000, 5));
        when(seatUsageMapper.findMaxSeats(TENANT, YM)).thenReturn(8);

        InvoiceResponse r = service().close(TENANT, YM);

        verify(invoiceMapper).insert(TENANT, YM, 8, 5, 3, 2000, 6000, 600, 6600);
        assertThat(r.status()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    @DisplayName("BILL-08: 이미 마감된 달 재마감은 409")
    void closeAlreadyIssuedConflicts() {
        when(invoiceMapper.find(TENANT, YM)).thenReturn(
                new Invoice(TENANT, YM, 8, 5, 3, 2000, 6000, 600, 6600, LocalDateTime.of(2026, 8, 1, 0, 0)));

        assertThatThrownBy(() -> service().close(TENANT, YM))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(409));
        verify(invoiceMapper, never()).insert(anyLong(), anyString(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("BILL-09: 청구월 형식이 틀리면 400")
    void invalidYmRejected() {
        assertThatThrownBy(() -> service().getInvoice(TENANT, "2026/07"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }
}
