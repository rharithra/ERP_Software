package in.retailflow.api.sales.service;

import in.retailflow.api.sales.domain.CreditTransactionType;
import in.retailflow.api.sales.domain.PaymentStatus;
import in.retailflow.api.sales.domain.ReturnStatusSummary;
import in.retailflow.api.sales.domain.Sale;
import in.retailflow.api.sales.domain.SaleItem;
import in.retailflow.api.sales.domain.SaleStatus;
import in.retailflow.api.sales.repository.CustomerCreditTransactionRepository;
import in.retailflow.api.sales.repository.PaymentRepository;
import in.retailflow.api.sales.repository.RefundRepository;
import in.retailflow.api.sales.repository.SaleReturnRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class SaleSettlementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private final PaymentRepository paymentRepository;
    private final SaleReturnRepository returnRepository;
    private final RefundRepository refundRepository;
    private final CustomerCreditTransactionRepository creditRepository;

    public SaleSettlementService(
            PaymentRepository paymentRepository,
            SaleReturnRepository returnRepository,
            RefundRepository refundRepository,
            CustomerCreditTransactionRepository creditRepository) {
        this.paymentRepository = paymentRepository;
        this.returnRepository = returnRepository;
        this.refundRepository = refundRepository;
        this.creditRepository = creditRepository;
    }

    public SaleSettlement snapshot(Sale sale) {
        BigDecimal original = scale(sale.getGrandTotal());
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            return new SaleSettlement(
                    original,
                    ZERO,
                    original,
                    ZERO,
                    ZERO,
                    ZERO,
                    original,
                    ZERO,
                    sale.getPaymentStatus() == null ? PaymentStatus.UNPAID : sale.getPaymentStatus(),
                    ReturnStatusSummary.NONE);
        }
        BigDecimal paid = actualPaid(sale);
        BigDecimal returns = scale(returnRepository.sumCompletedTotalForSale(sale.getId()));
        BigDecimal net = original.subtract(returns).max(ZERO);
        BigDecimal applied = scale(creditRepository.sumBySaleAndType(sale.getId(), CreditTransactionType.CREDIT_APPLIED));
        BigDecimal refunded = scale(refundRepository.sumCompletedForSale(sale.getId()));
        BigDecimal settlement = paid.add(applied);
        BigDecimal outstanding = net.subtract(settlement).max(ZERO);
        BigDecimal created = scale(creditRepository.sumBySaleAndType(sale.getId(), CreditTransactionType.CREDIT_CREATED));
        BigDecimal refundedCredit = scale(creditRepository.sumBySaleAndType(sale.getId(), CreditTransactionType.CREDIT_REFUNDED));
        BigDecimal appliedFrom = scale(creditRepository.sumAppliedFromSale(sale.getId()));
        BigDecimal ledgerCredit = created.subtract(refundedCredit).subtract(appliedFrom).max(ZERO);
        BigDecimal formulaCredit = settlement.subtract(net).subtract(refunded).max(ZERO);
        BigDecimal customerCredit;
        if (sale.getCustomer() == null || created.compareTo(ZERO) == 0) {
            customerCredit = formulaCredit;
        } else {
            customerCredit = ledgerCredit;
        }
        PaymentStatus status = statusFor(outstanding, settlement, customerCredit);
        return new SaleSettlement(
                original,
                returns,
                net,
                paid,
                applied,
                refunded,
                outstanding,
                customerCredit,
                status,
                returnSummary(sale, returns, original));
    }

    public BigDecimal actualPaid(Sale sale) {
        BigDecimal sum = scale(paymentRepository.sumBySaleId(sale.getId()));
        if (sum.compareTo(ZERO) == 0
                && sale.getPaymentStatus() == PaymentStatus.PAID
                && sale.getSalesOrderId() == null) {
            return scale(sale.getGrandTotal());
        }
        return sum;
    }

    public static PaymentStatus statusFor(BigDecimal outstanding, BigDecimal settlement, BigDecimal customerCredit) {
        if (customerCredit.compareTo(ZERO) > 0) {
            return PaymentStatus.REFUND_DUE;
        }
        if (outstanding.compareTo(ZERO) <= 0) {
            return PaymentStatus.PAID;
        }
        if (settlement.compareTo(ZERO) <= 0) {
            return PaymentStatus.UNPAID;
        }
        return PaymentStatus.PARTIALLY_PAID;
    }

    private ReturnStatusSummary returnSummary(Sale sale, BigDecimal returnTotal, BigDecimal original) {
        if (returnTotal.compareTo(ZERO) <= 0) {
            return ReturnStatusSummary.NONE;
        }
        boolean allReturned = true;
        if (sale.getItems() != null && !sale.getItems().isEmpty()) {
            for (SaleItem item : sale.getItems()) {
                BigDecimal done = returnRepository.sumCompletedQuantityForSaleItem(item.getId());
                if (done.compareTo(item.getQuantity()) < 0) {
                    allReturned = false;
                    break;
                }
            }
        } else {
            allReturned = returnTotal.compareTo(original) >= 0;
        }
        return allReturned ? ReturnStatusSummary.FULLY_RETURNED : ReturnStatusSummary.PARTIALLY_RETURNED;
    }

    private static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record SaleSettlement(
            BigDecimal originalTotal,
            BigDecimal completedReturnAmount,
            BigDecimal netSaleAmount,
            BigDecimal actualPaidAmount,
            BigDecimal customerCreditApplied,
            BigDecimal completedRefundAmount,
            BigDecimal outstandingAmount,
            BigDecimal customerCreditAmount,
            PaymentStatus paymentStatus,
            ReturnStatusSummary returnStatus) {}
}
