package com.attendance.pro.billing;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.attendance.pro.billing.BillingDtos.BillingProfileRequest;
import com.attendance.pro.billing.BillingDtos.BillingProfileResponse;
import com.attendance.pro.billing.BillingDtos.InvoiceResponse;
import com.attendance.pro.billing.BillingDtos.InvoiceStatus;
import com.attendance.pro.common.ApiException;
import com.attendance.pro.tenant.TenantBilling;
import com.attendance.pro.tenant.TenantBillingMapper;
import com.attendance.pro.tenant.TenantDtos.BillingMethod;

/**
 * 인당(seat) 과금 계산·청구서 서비스.
 *
 * 과금식(전부 원, VAT 별도 라인):
 *   과금인원 = max(0, 월중최대활성 - 무료인원)
 *   공급가   = 과금인원 × 인당단가
 *   부가세   = round(공급가 × 10%)
 *   합계     = 공급가 + 부가세
 *
 * 월중 최대 활성은 {@code tenant_seat_usage}의 high-water mark로 추적한다(멤버 활성/비활성/삭제와
 * 청구서 조회 시 {@link #touchSeatUsage}로 갱신). 진행 중인 달은 현재 활성 수까지 반영한 <b>잠정</b>이고,
 * 마감({@link #close})하면 그 시점 값을 {@code invoice}에 스냅샷해 <b>확정</b>한다(이후 단가 변경 소급 없음).
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    /** tenant_billing 행이 없을 때의 기본값(V22 컬럼 DEFAULT와 일치). */
    static final int DEFAULT_PER_SEAT = 2000;
    static final int DEFAULT_FREE_SEATS = 5;
    /** 청구서 목록에 보여줄 최근 개월 수(현재 달 포함). */
    private static final int MONTHS_WINDOW = 12;

    private final TenantBillingMapper tenantBillingMapper;
    private final SeatUsageMapper seatUsageMapper;
    private final InvoiceMapper invoiceMapper;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public BillingService(TenantBillingMapper tenantBillingMapper, SeatUsageMapper seatUsageMapper,
            InvoiceMapper invoiceMapper) {
        this(tenantBillingMapper, seatUsageMapper, invoiceMapper, Clock.systemDefaultZone());
    }

    BillingService(TenantBillingMapper tenantBillingMapper, SeatUsageMapper seatUsageMapper,
            InvoiceMapper invoiceMapper, Clock clock) {
        this.tenantBillingMapper = tenantBillingMapper;
        this.seatUsageMapper = seatUsageMapper;
        this.invoiceMapper = invoiceMapper;
        this.clock = clock;
    }

    /**
     * 현재 달 좌석 사용량 high-water mark를 갱신한다(멤버 활성/비활성/삭제·청구서 조회 훅).
     * 부가 기능 — 실패해도 본 요청을 깨지 않는다(경고 로그만).
     */
    public void touchSeatUsage(long tenantId) {
        try {
            seatUsageMapper.touch(tenantId, currentYm());
        } catch (Exception e) {
            log.warn("seat usage touch failed: tenant={}", tenantId, e);
        }
    }

    /** 회사의 최근 {@value #MONTHS_WINDOW}개월 청구서(현재 달 잠정 + 마감월 확정), 최신월 우선. */
    public List<InvoiceResponse> listForTenant(long tenantId) {
        touchSeatUsage(tenantId);
        TenantBilling config = tenantBillingMapper.findById(tenantId);
        YearMonth cursor = YearMonth.now(clock);
        List<InvoiceResponse> result = new ArrayList<>();
        for (int i = 0; i < MONTHS_WINDOW; i++) {
            result.add(resolve(tenantId, cursor.toString(), config));
            cursor = cursor.minusMonths(1);
        }
        return result;
    }

    /** 회사에 보여줄 계약 요약(#14, 읽기전용) — 요금제·인당 단가·무료 좌석. 미등록이면 기본값. */
    public com.attendance.pro.billing.BillingDtos.ContractSummaryResponse getContractSummary(long tenantId) {
        TenantBilling config = tenantBillingMapper.findById(tenantId);
        if (config == null) {
            return new com.attendance.pro.billing.BillingDtos.ContractSummaryResponse("BASIC", 2000, 5);
        }
        return new com.attendance.pro.billing.BillingDtos.ContractSummaryResponse(
                config.plan(), config.perSeatAmount(), config.freeSeats());
    }

    /** 회사 자사 결제 정보 조회(#14) — 미등록이면 기본값(계좌이체/미입력)을 돌려준다. */
    public BillingProfileResponse getProfile(long tenantId) {
        TenantBilling config = tenantBillingMapper.findById(tenantId);
        if (config == null) {
            return new BillingProfileResponse(BillingMethod.INVOICE, null, null);
        }
        return new BillingProfileResponse(config.billingMethod(), config.billingEmail(), config.memo());
    }

    /** 회사 자사 결제 정보 등록/수정(#14) — 결제수단·청구 이메일·비고만(가격 필드 불변). */
    public BillingProfileResponse updateProfile(long tenantId, BillingProfileRequest req) {
        String email = req.billingEmail() == null || req.billingEmail().isBlank() ? null : req.billingEmail().trim();
        String memo = req.memo() == null || req.memo().isBlank() ? null : req.memo().trim();
        tenantBillingMapper.upsertProfile(tenantId, req.billingMethod(), email, memo);
        return getProfile(tenantId);
    }

    /** 특정 월 청구서 조회(확정이면 스냅샷, 아니면 실시간 잠정 계산). */
    public InvoiceResponse getInvoice(long tenantId, String ym) {
        validateYm(ym);
        touchSeatUsage(tenantId);
        return resolve(tenantId, ym, tenantBillingMapper.findById(tenantId));
    }

    /**
     * 월 마감(확정) — 그 시점의 월중 최대 활성·단가·무료인원을 {@code invoice}에 스냅샷한다.
     * 이미 확정된 달이면 409(재마감 방지 — 확정 청구서는 불변).
     */
    public InvoiceResponse close(long tenantId, String ym) {
        validateYm(ym);
        if (invoiceMapper.find(tenantId, ym) != null) {
            throw ApiException.conflict("INVOICE_ALREADY_ISSUED", "billing.invoice.already-issued");
        }
        touchSeatUsage(tenantId);
        TenantBilling config = tenantBillingMapper.findById(tenantId);
        Line line = compute(tenantId, ym, config);
        invoiceMapper.insert(tenantId, ym, line.maxSeats, line.freeSeats, line.billedSeats,
                line.unitPrice, line.subtotal, line.vat, line.total);
        return InvoiceResponse.issued(invoiceMapper.find(tenantId, ym));
    }

    /** 확정 청구서가 있으면 그것을, 없으면 잠정 계산 결과를 돌려준다. */
    private InvoiceResponse resolve(long tenantId, String ym, TenantBilling config) {
        Invoice issued = invoiceMapper.find(tenantId, ym);
        if (issued != null) {
            return InvoiceResponse.issued(issued);
        }
        Line line = compute(tenantId, ym, config);
        return new InvoiceResponse(ym, line.maxSeats, line.freeSeats, line.billedSeats,
                line.unitPrice, line.subtotal, line.vat, line.total, InvoiceStatus.PROVISIONAL, null);
    }

    /** 잠정 금액 산정. 진행 중인 달만 현재 활성 수를 반영(과거 달은 기록된 최대만). */
    private Line compute(long tenantId, String ym, TenantBilling config) {
        int unitPrice = config == null ? DEFAULT_PER_SEAT : config.perSeatAmount();
        int freeSeats = config == null ? DEFAULT_FREE_SEATS : config.freeSeats();
        Integer stored = seatUsageMapper.findMaxSeats(tenantId, ym);
        int maxSeats = stored == null ? 0 : stored;
        int billedSeats = Math.max(0, maxSeats - freeSeats);
        //단가 상한(1천만)×좌석이면 int를 넘기므로 long으로 계산·저장(오버플로 방지)
        long subtotal = (long) billedSeats * unitPrice;
        long vat = Math.round(subtotal / 10.0);
        return new Line(maxSeats, freeSeats, billedSeats, unitPrice, subtotal, vat, subtotal + vat);
    }

    private String currentYm() {
        return YearMonth.now(clock).toString();
    }

    private void validateYm(String ym) {
        try {
            YearMonth.parse(ym); //YYYY-MM
        } catch (DateTimeParseException | NullPointerException e) {
            throw ApiException.badRequest("INVALID_YM", "billing.invoice.invalid-ym");
        }
    }

    private record Line(int maxSeats, int freeSeats, int billedSeats,
            int unitPrice, long subtotal, long vat, long total) {
    }
}
