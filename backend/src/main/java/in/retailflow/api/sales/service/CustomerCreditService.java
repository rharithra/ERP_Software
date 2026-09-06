package in.retailflow.api.sales.service;

import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.identity.domain.UserAccount;
import in.retailflow.api.identity.repository.UserAccountRepository;
import in.retailflow.api.sales.domain.CreditTransactionType;
import in.retailflow.api.sales.domain.Customer;
import in.retailflow.api.sales.domain.CustomerCreditTransaction;
import in.retailflow.api.sales.domain.Refund;
import in.retailflow.api.sales.domain.Sale;
import in.retailflow.api.sales.domain.SaleReturn;
import in.retailflow.api.sales.domain.SaleStatus;
import in.retailflow.api.sales.dto.CreditTransactionResponse;
import in.retailflow.api.sales.dto.CustomerCreditResponse;
import in.retailflow.api.sales.dto.CustomerFinancialResponse;
import in.retailflow.api.sales.repository.CustomerCreditTransactionRepository;
import in.retailflow.api.sales.repository.CustomerRepository;
import in.retailflow.api.sales.repository.SaleRepository;
import in.retailflow.api.security.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerCreditService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private final CustomerCreditTransactionRepository creditRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final UserAccountRepository userAccountRepository;
    private final SaleSettlementService settlementService;

    public CustomerCreditService(
            CustomerCreditTransactionRepository creditRepository,
            CustomerRepository customerRepository,
            SaleRepository saleRepository,
            UserAccountRepository userAccountRepository,
            SaleSettlementService settlementService) {
        this.creditRepository = creditRepository;
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.userAccountRepository = userAccountRepository;
        this.settlementService = settlementService;
    }

    @Transactional(readOnly = true)
    public CustomerCreditResponse forCustomer(UUID customerId) {
        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.CUSTOMER_NOT_FOUND, "Customer not found", HttpStatus.NOT_FOUND.value()));
        return new CustomerCreditResponse(
                customer.getId().toString(),
                customer.getName(),
                available(customerId),
                transactions(customerId));
    }

    @Transactional(readOnly = true)
    public List<CreditTransactionResponse> transactions(UUID customerId) {
        return creditRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    public BigDecimal available(UUID customerId) {
        return scale(creditRepository.balanceForCustomer(customerId)).max(ZERO);
    }

    @Transactional(readOnly = true)
    public CustomerFinancialResponse financial(UUID customerId) {
        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.CUSTOMER_NOT_FOUND, "Customer not found", HttpStatus.NOT_FOUND.value()));
        BigDecimal sales = ZERO;
        BigDecimal paid = ZERO;
        BigDecimal outstanding = ZERO;
        for (Sale sale : saleRepository.findByCustomer_Id(customerId)) {
            if (sale.getStatus() != SaleStatus.COMPLETED) {
                continue;
            }
            SaleSettlementService.SaleSettlement snap = settlementService.snapshot(sale);
            sales = sales.add(snap.originalTotal());
            paid = paid.add(snap.actualPaidAmount());
            outstanding = outstanding.add(snap.outstandingAmount());
        }
        return new CustomerFinancialResponse(
                customer.getId().toString(), customer.getName(), sales, paid, outstanding, available(customerId));
    }

    public void lockCustomer(Sale sale) {
        if (sale.getCustomer() == null) {
            return;
        }
        customerRepository.findByIdForUpdate(sale.getCustomer().getId()).orElseThrow(() -> new RetailflowException(
                ErrorCodes.CUSTOMER_NOT_FOUND, "Customer not found", HttpStatus.NOT_FOUND.value()));
    }

    public BigDecimal availableLiability() {
        return scale(creditRepository.sumCreated())
                .subtract(scale(creditRepository.sumApplied()))
                .subtract(scale(creditRepository.sumRefunded()))
                .max(ZERO);
    }

    public void syncCreditCreated(Sale sale, SaleReturn saleReturn) {
        if (sale.getCustomer() == null) {
            return;
        }
        lockCustomer(sale);
        SaleSettlementService.SaleSettlement snap = settlementService.snapshot(sale);
        BigDecimal desired = snap.actualPaidAmount().subtract(snap.netSaleAmount()).max(ZERO);
        BigDecimal existing = scale(creditRepository.sumBySaleAndType(sale.getId(), CreditTransactionType.CREDIT_CREATED));
        BigDecimal delta = desired.subtract(existing);
        if (delta.compareTo(ZERO) <= 0) {
            return;
        }
        CustomerCreditTransaction tx = new CustomerCreditTransaction(
                UUID.randomUUID(),
                sale.getTenant(),
                sale.getCustomer(),
                CreditTransactionType.CREDIT_CREATED,
                delta,
                TenantContext.require().userId(),
                "Return " + saleReturn.getReturnNumber());
        tx.setSale(sale);
        tx.setSaleReturn(saleReturn);
        creditRepository.save(tx);
    }

    public void applyToSale(Sale sale, BigDecimal amount) {
        BigDecimal credit = scale(amount);
        if (credit.compareTo(ZERO) <= 0) {
            return;
        }
        if (sale.getCustomer() == null) {
            throw new RetailflowException(
                    ErrorCodes.CUSTOMER_CREDIT_INSUFFICIENT,
                    "Customer credit can only be applied to a named customer",
                    HttpStatus.BAD_REQUEST.value());
        }
        lockCustomer(sale);
        BigDecimal available = available(sale.getCustomer().getId());
        if (credit.compareTo(available) > 0) {
            throw new RetailflowException(
                    ErrorCodes.CUSTOMER_CREDIT_INSUFFICIENT,
                    "Available customer credit is ₹" + available.toPlainString(),
                    HttpStatus.BAD_REQUEST.value());
        }
        SaleSettlementService.SaleSettlement snap = settlementService.snapshot(sale);
        BigDecimal maxApply = snap.outstandingAmount();
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            maxApply = scale(sale.getGrandTotal());
        }
        if (credit.compareTo(maxApply) > 0) {
            throw new RetailflowException(
                    ErrorCodes.CUSTOMER_CREDIT_INSUFFICIENT,
                    "Credit applied cannot exceed the remaining sale amount of ₹" + maxApply.toPlainString(),
                    HttpStatus.BAD_REQUEST.value());
        }
        BigDecimal remaining = credit;
        List<CustomerCreditTransaction> created = creditRepository.findByCustomer_IdAndTransactionTypeOrderByCreatedAtAsc(
                sale.getCustomer().getId(), CreditTransactionType.CREDIT_CREATED);
        for (CustomerCreditTransaction origin : created) {
            if (remaining.compareTo(ZERO) <= 0) {
                break;
            }
            Sale source = origin.getSale();
            BigDecimal sourceRemaining = source == null
                    ? origin.getAmount()
                    : settlementService.snapshot(source).customerCreditAmount();
            BigDecimal take = remaining.min(sourceRemaining);
            if (take.compareTo(ZERO) <= 0) {
                continue;
            }
            CustomerCreditTransaction tx = new CustomerCreditTransaction(
                    UUID.randomUUID(),
                    sale.getTenant(),
                    sale.getCustomer(),
                    CreditTransactionType.CREDIT_APPLIED,
                    take,
                    TenantContext.require().userId(),
                    "Applied to " + sale.getSaleNumber());
            tx.setSale(sale);
            tx.setSourceSale(source);
            creditRepository.save(tx);
            remaining = remaining.subtract(take);
        }
        if (remaining.compareTo(ZERO) > 0) {
            throw new RetailflowException(
                    ErrorCodes.CUSTOMER_CREDIT_INSUFFICIENT,
                    "Available customer credit is ₹" + available.toPlainString(),
                    HttpStatus.BAD_REQUEST.value());
        }
    }

    public void recordRefunded(Sale sale, Refund refund) {
        if (sale.getCustomer() == null) {
            return;
        }
        lockCustomer(sale);
        CustomerCreditTransaction tx = new CustomerCreditTransaction(
                UUID.randomUUID(),
                sale.getTenant(),
                sale.getCustomer(),
                CreditTransactionType.CREDIT_REFUNDED,
                refund.getAmount(),
                TenantContext.require().userId(),
                "Refund " + refund.getRefundNumber());
        tx.setSale(sale);
        tx.setRefund(refund);
        creditRepository.save(tx);
    }

    private CreditTransactionResponse toResponse(CustomerCreditTransaction tx) {
        String name = userAccountRepository.findById(tx.getCreatedBy()).map(UserAccount::getFullName).orElse("Staff");
        return new CreditTransactionResponse(
                tx.getId().toString(),
                tx.getTransactionType(),
                tx.getAmount(),
                tx.getSale() == null ? null : tx.getSale().getId().toString(),
                tx.getSaleReturn() == null ? null : tx.getSaleReturn().getId().toString(),
                tx.getRefund() == null ? null : tx.getRefund().getId().toString(),
                tx.getReference(),
                tx.getCreatedBy().toString(),
                name,
                tx.getCreatedAt());
    }

    private static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
