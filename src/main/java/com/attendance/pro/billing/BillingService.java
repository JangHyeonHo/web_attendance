package com.attendance.pro.billing;

import java.time.Clock;
import java.time.LocalDate;
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
import com.attendance.pro.tenant.Tenant;
import com.attendance.pro.tenant.TenantBilling;
import com.attendance.pro.tenant.TenantBillingMapper;
import com.attendance.pro.tenant.TenantMapper;
import com.attendance.pro.tenant.TenantDtos.BillingMethod;

/**
 * 인당(seat) 과금 계산·청구서 서비스 — <b>등록 시점 일할계산</b>(seat-day proration).
 *
 * 정책(docs/billing-calculation.md §4 확정 v1):
 * <ul>
 *   <li>증원/무료→유료(업그레이드): 등록일 <b>다음 날부터</b> 월말까지 일할 과금(전환 당일 제외).</li>
 *   <li>감원/유료→무료(다운그레이드): <b>당월은 감액·환불 없음</b>, 감소분은 다음 달 1일부터 반영(비대칭).</li>
 * </ul>
 *
 * 과금식(전부 원, VAT 별도 라인):
 * <pre>
 *   과금좌석(billable)  = max(0, 활성좌석 − 무료좌석)
 *   기초좌석            = 그 달 1일 시점의 과금좌석(전월 말 이월) — 그 달 전 일수 과금
 *   좌석일(seat-days)   = 기초좌석 × 월일수 + Σ_증원( Δ과금좌석 × (월일수 − 등록일) )
 *   공급가              = round( 좌석일 × 인당월단가 ÷ 월일수 )
 *   부가세              = round(공급가 × 10%)
 *   합계                = 공급가 + 부가세
 * </pre>
 *
 * 좌석 변동은 {@code seat_change_event}에 append하고({@link #touchSeatUsage}), 청구 시 그 달 이벤트를
 * 재생해 좌석일을 산정한다. 진행 중인 달은 <b>잠정</b>(기초좌석을 월말까지 유지한다고 가정한 실시간 계산),
 * 마감({@link #close})하면 그 시점 좌석일·단가·인원을 {@code invoice}에 스냅샷해 <b>확정</b>한다.
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
    private final SeatEventMapper seatEventMapper;
    private final InvoiceMapper invoiceMapper;
    private final TenantMapper tenantMapper;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public BillingService(TenantBillingMapper tenantBillingMapper, SeatEventMapper seatEventMapper,
            InvoiceMapper invoiceMapper, TenantMapper tenantMapper) {
        this(tenantBillingMapper, seatEventMapper, invoiceMapper, tenantMapper, Clock.systemDefaultZone());
    }

    BillingService(TenantBillingMapper tenantBillingMapper, SeatEventMapper seatEventMapper,
            InvoiceMapper invoiceMapper, TenantMapper tenantMapper, Clock clock) {
        this.tenantBillingMapper = tenantBillingMapper;
        this.seatEventMapper = seatEventMapper;
        this.invoiceMapper = invoiceMapper;
        this.tenantMapper = tenantMapper;
        this.clock = clock;
    }

    /**
     * 활성 좌석 변동을 기록한다(멤버 활성/비활성/삭제·초대 활성화 훅).
     * 직전 기록값과 같으면 append하지 않는다(조회 등으로 인한 중복 방지).
     * 부가 기능 — 실패해도 본 요청을 깨지 않는다(경고 로그만).
     */
    public void touchSeatUsage(long tenantId) {
        try {
            int active = seatEventMapper.countActiveSeats(tenantId);
            Integer last = seatEventMapper.lastActiveSeats(tenantId);
            if (last == null || last.intValue() != active) {
                seatEventMapper.insert(tenantId, LocalDate.now(clock), active);
            }
        } catch (Exception e) {
            log.warn("seat change record failed: tenant={}", tenantId, e);
        }
    }

    /**
     * 회사 청구서 목록(현재 달 잠정 + 마감월 확정), 최신월 우선.
     * 회사 <b>생성월</b>부터 현재 달까지만 보여준다(가입 전 달은 청구 대상이 아니므로 노출하지 않음).
     * 상한은 {@value #MONTHS_WINDOW}개월(아주 오래된 회사도 최근 1년만).
     */
    public List<InvoiceResponse> listForTenant(long tenantId) {
        touchSeatUsage(tenantId);
        TenantBilling config = tenantBillingMapper.findById(tenantId);
        YearMonth cursor = YearMonth.now(clock);
        YearMonth createdMonth = tenantCreatedMonth(tenantId);
        List<InvoiceResponse> result = new ArrayList<>();
        for (int i = 0; i < MONTHS_WINDOW; i++) {
            result.add(resolve(tenantId, cursor.toString(), config));
            //생성월에 도달하면 그 이전(가입 전) 달은 제외
            if (createdMonth != null && !cursor.isAfter(createdMonth)) {
                break;
            }
            cursor = cursor.minusMonths(1);
        }
        return result;
    }

    /** 회사 생성월(tenant.created_at). 조회 실패 시 null → 상한(12개월)만 적용. */
    private YearMonth tenantCreatedMonth(long tenantId) {
        Tenant tenant = tenantMapper.findById(tenantId);
        if (tenant == null || tenant.createdAt() == null) {
            return null;
        }
        return YearMonth.from(tenant.createdAt());
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
     * 월 마감(확정) — 그 시점의 좌석일·단가·인원을 {@code invoice}에 스냅샷한다.
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
                line.seatDays, line.daysInMonth, line.unitPrice, line.subtotal, line.vat, line.total);
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
                line.seatDays, line.daysInMonth, line.unitPrice, line.subtotal, line.vat, line.total,
                InvoiceStatus.PROVISIONAL, null);
    }

    /**
     * 좌석일 기반 잠정 금액 산정(그 달 좌석 변동 이벤트 재생).
     * 감원은 chargedLevel(과금 기준선)을 낮추지 않아 당월에 반영되지 않고, 증원만 등록일 다음 날부터 누적된다.
     */
    private Line compute(long tenantId, String ym, TenantBilling config) {
        int unitPrice = config == null ? DEFAULT_PER_SEAT : config.perSeatAmount();
        int freeSeats = config == null ? DEFAULT_FREE_SEATS : config.freeSeats();
        YearMonth month = YearMonth.parse(ym);
        int daysInMonth = month.lengthOfMonth();
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();

        Integer before = seatEventMapper.activeBefore(tenantId, first);
        int startActive = before == null ? 0 : before;
        int base = Math.max(0, startActive - freeSeats);   //이월 기초좌석 — 그 달 전 일수 과금
        int chargedLevel = base;                            //과금 기준선(월중 최대 과금좌석, 감원엔 안 내려감)
        int peakActive = startActive;                       //표시용 최대 활성 수(무료 포함)
        long accrued = 0;                                   //증원분 좌석일 누적

        for (SeatEvent e : seatEventMapper.inMonth(tenantId, first, last)) {
            peakActive = Math.max(peakActive, e.activeSeats());
            int billable = Math.max(0, e.activeSeats() - freeSeats);
            if (billable > chargedLevel) {                  //증원(기준선 상향)만 일할 누적
                int day = e.eventDate().getDayOfMonth();
                accrued += (long) (billable - chargedLevel) * (daysInMonth - day); //등록 당일 제외
                chargedLevel = billable;
            }
        }

        long seatDays = (long) base * daysInMonth + accrued;
        long subtotal = Math.round((double) (seatDays * unitPrice) / daysInMonth);
        long vat = Math.round(subtotal / 10.0);
        return new Line(peakActive, freeSeats, chargedLevel, unitPrice, seatDays, daysInMonth,
                subtotal, vat, subtotal + vat);
    }

    private void validateYm(String ym) {
        try {
            YearMonth.parse(ym); //YYYY-MM
        } catch (DateTimeParseException | NullPointerException e) {
            throw ApiException.badRequest("INVALID_YM", "billing.invoice.invalid-ym");
        }
    }

    private record Line(int maxSeats, int freeSeats, int billedSeats, int unitPrice,
            long seatDays, int daysInMonth, long subtotal, long vat, long total) {
    }
}
