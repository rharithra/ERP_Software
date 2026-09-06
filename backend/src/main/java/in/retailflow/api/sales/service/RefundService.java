package in.retailflow.api.sales.service;

import in.retailflow.api.common.DocumentNumbers;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.identity.domain.UserAccount;
import in.retailflow.api.identity.repository.UserAccountRepository;
import in.retailflow.api.sales.domain.Refund;
import in.retailflow.api.sales.domain.RefundNumberCounter;
import in.retailflow.api.sales.domain.RefundStatus;
import in.retailflow.api.sales.domain.Sale;
import in.retailflow.api.sales.domain.SaleReturn;
import in.retailflow.api.sales.dto.RefundRequest;
import in.retailflow.api.sales.dto.RefundResponse;
import in.retailflow.api.sales.repository.RefundNumberCounterRepository;
import in.retailflow.api.sales.repository.RefundRepository;
import in.retailflow.api.sales.repository.SaleRepository;
import in.retailflow.api.sales.repository.SaleReturnRepository;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {

    private final RefundRepository refundRepository;
    private final RefundNumberCounterRepository counterRepository;
    private final SaleRepository saleRepository;
    private final SaleReturnRepository returnRepository;
    private final CustomerCreditService creditService;
    private final PaymentService paymentService;
    private final SaleSettlementService settlementService;
    private final UserAccountRepository userAccountRepository;

    public RefundService(
            RefundRepository refundRepository,
            RefundNumberCounterRepository counterRepository,
            SaleRepository saleRepository,
            SaleReturnRepository returnRepository,
            CustomerCreditService creditService,
            PaymentService paymentService,
            SaleSettlementService settlementService,
            UserAccountRepository userAccountRepository) {
        this.refundRepository = refundRepository;
        this.counterRepository = counterRepository;
        this.saleRepository = saleRepository;
        this.returnRepository = returnRepository;
        this.creditService = creditService;
        this.paymentService = paymentService;
        this.settlementService = settlementService;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> list(String q, RefundStatus status, UUID saleId, UUID customerId) {
        if (saleId != null) {
            return refundRepository.findBySale_IdOrderByCreatedAtDesc(saleId).stream()
                    .filter(refund -> matches(refund, q, status))
                    .map(this::toResponse)
                    .toList();
        }
        if (customerId != null) {
            return refundRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId).stream()
                    .filter(refund -> matches(refund, q, status))
                    .map(this::toResponse)
                    .toList();
        }
        return refundRepository.search(q, status == null ? null : status.name()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RefundResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public RefundResponse create(RefundRequest request) {
        Sale sale = saleRepository
                .findByIdForUpdate(request.saleId())
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.SALE_NOT_FOUND, "Sale not found", HttpStatus.NOT_FOUND.value()));
        creditService.lockCustomer(sale);
        SaleReturn saleReturn = null;
        if (request.saleReturnId() != null) {
            saleReturn = returnRepository
                    .findById(request.saleReturnId())
                    .orElseThrow(() -> new RetailflowException(
                            ErrorCodes.RETURN_NOT_FOUND, "Return not found", HttpStatus.NOT_FOUND.value()));
            if (!saleReturn.getSale().getId().equals(sale.getId())) {
                throw new RetailflowException(
                        ErrorCodes.VALIDATION_ERROR, "Return does not belong to this sale", HttpStatus.BAD_REQUEST.value());
            }
        }
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        rejectIfExceedsCredit(sale, amount);
        Refund refund = new Refund(
                UUID.randomUUID(),
                sale.getTenant(),
                nextNumber(sale.getTenant()),
                sale,
                saleReturn,
                amount,
                request.paymentMethod(),
                TenantContext.require().userId());
        refund.setReferenceNumber(blank(request.referenceNumber()));
        refund.setNotes(blank(request.notes()));
        return toResponse(refundRepository.save(refund));
    }

    @Transactional
    public RefundResponse complete(UUID id) {
        Refund refund = refundRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.REFUND_NOT_FOUND, "Refund not found", HttpStatus.NOT_FOUND.value()));
        Sale sale = saleRepository
                .findByIdForUpdate(refund.getSale().getId())
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.SALE_NOT_FOUND, "Sale not found", HttpStatus.NOT_FOUND.value()));
        creditService.lockCustomer(sale);
        rejectIfExceedsCredit(sale, refund.getAmount());
        refund.markCompleted(TenantContext.require().userId(), Instant.now());
        creditService.recordRefunded(sale, refund);
        paymentService.applySaleTotals(sale);
        return toResponse(refund);
    }

    @Transactional
    public RefundResponse cancel(UUID id) {
        Refund refund = refundRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.REFUND_NOT_FOUND, "Refund not found", HttpStatus.NOT_FOUND.value()));
        refund.markCancelled();
        return toResponse(refund);
    }

    private void rejectIfExceedsCredit(Sale sale, BigDecimal amount) {
        SaleSettlementService.SaleSettlement snap = settlementService.snapshot(sale);
        BigDecimal available = snap.customerCreditAmount();
        if (sale.getCustomer() != null) {
            available = available.min(creditService.available(sale.getCustomer().getId()));
        }
        if (amount.compareTo(available) > 0) {
            throw new RetailflowException(
                    ErrorCodes.REFUND_EXCEEDS_AVAILABLE_CREDIT,
                    "Refund cannot exceed available customer credit of ₹" + available.toPlainString(),
                    HttpStatus.BAD_REQUEST.value());
        }
    }

    private boolean matches(Refund refund, String q, RefundStatus status) {
        if (status != null && refund.getStatus() != status) {
            return false;
        }
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = q.toLowerCase();
        return refund.getRefundNumber().toLowerCase().contains(needle)
                || refund.getSale().getSaleNumber().toLowerCase().contains(needle)
                || (refund.getSale().getInvoiceNumber() != null
                        && refund.getSale().getInvoiceNumber().toLowerCase().contains(needle));
    }

    private String nextNumber(Tenant tenant) {
        RefundNumberCounter counter = counterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseGet(() -> {
                    try {
                        counterRepository.saveAndFlush(new RefundNumberCounter(tenant));
                    } catch (DataIntegrityViolationException ignored) {
                    }
                    return counterRepository
                            .findByTenantIdForUpdate(tenant.getId())
                            .orElseThrow(() -> new RetailflowException(
                                    ErrorCodes.INTERNAL_ERROR,
                                    "Unable to allocate a refund number",
                                    HttpStatus.INTERNAL_SERVER_ERROR.value()));
                });
        return DocumentNumbers.format("REF-", counter.nextValue());
    }

    private Refund require(UUID id) {
        return refundRepository
                .findById(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.REFUND_NOT_FOUND, "Refund not found", HttpStatus.NOT_FOUND.value()));
    }

    private RefundResponse toResponse(Refund refund) {
        String createdByName = userAccountRepository
                .findById(refund.getCreatedBy())
                .map(UserAccount::getFullName)
                .orElse("Staff");
        Sale sale = refund.getSale();
        return new RefundResponse(
                refund.getId().toString(),
                refund.getRefundNumber(),
                sale.getId().toString(),
                sale.getSaleNumber(),
                sale.getInvoiceNumber(),
                refund.getSaleReturn() == null ? null : refund.getSaleReturn().getId().toString(),
                refund.getCustomer() == null ? null : refund.getCustomer().getId().toString(),
                sale.getCustomerName(),
                refund.getAmount(),
                refund.getPaymentMethod(),
                refund.getStatus(),
                refund.getReferenceNumber(),
                refund.getNotes(),
                refund.getCreatedBy().toString(),
                createdByName,
                refund.getCreatedAt(),
                refund.getCompletedAt());
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
