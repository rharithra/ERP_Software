package in.retailflow.api.sales.service;

import in.retailflow.api.common.DocumentNumbers;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.identity.domain.UserAccount;
import in.retailflow.api.identity.repository.UserAccountRepository;
import in.retailflow.api.pipeline.domain.PipelineActivityType;
import in.retailflow.api.pipeline.domain.SalesOrder;
import in.retailflow.api.pipeline.repository.SalesOrderRepository;
import in.retailflow.api.pipeline.service.LeadService;
import in.retailflow.api.pipeline.service.NotificationService;
import in.retailflow.api.pipeline.service.PipelineSupport;
import in.retailflow.api.sales.domain.Payment;
import in.retailflow.api.sales.domain.PaymentMethod;
import in.retailflow.api.sales.domain.PaymentNumberCounter;
import in.retailflow.api.sales.domain.PaymentStatus;
import in.retailflow.api.sales.domain.Sale;
import in.retailflow.api.sales.dto.OutstandingRowResponse;
import in.retailflow.api.sales.dto.PaymentRequest;
import in.retailflow.api.sales.dto.PaymentResponse;
import in.retailflow.api.sales.repository.PaymentNumberCounterRepository;
import in.retailflow.api.sales.repository.PaymentRepository;
import in.retailflow.api.sales.repository.SaleRepository;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentNumberCounterRepository counterRepository;
    private final SaleRepository saleRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final UserAccountRepository userAccountRepository;
    private final PipelineSupport support;
    private final LeadService leadService;
    private final NotificationService notificationService;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentNumberCounterRepository counterRepository,
            SaleRepository saleRepository,
            SalesOrderRepository salesOrderRepository,
            UserAccountRepository userAccountRepository,
            PipelineSupport support,
            LeadService leadService,
            NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.counterRepository = counterRepository;
        this.saleRepository = saleRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.userAccountRepository = userAccountRepository;
        this.support = support;
        this.leadService = leadService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> forSale(UUID saleId) {
        return paymentRepository.findBySaleIdOrderByPaymentDateAscCreatedAtAsc(saleId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> forOrder(UUID orderId) {
        return paymentRepository.findBySalesOrderIdOrderByPaymentDateAscCreatedAtAsc(orderId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> forCustomer(UUID customerId) {
        return paymentRepository.findByCustomerIdOrderByPaymentDateDescCreatedAtDesc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OutstandingRowResponse> outstanding(String filter) {
        String mode = filter == null ? "OUTSTANDING" : filter.toUpperCase();
        List<OutstandingRowResponse> rows = new ArrayList<>();
        for (Sale sale : saleRepository.findAll()) {
            if (sale.getStatus() != in.retailflow.api.sales.domain.SaleStatus.COMPLETED) {
                continue;
            }
            BigDecimal paid = paidForSale(sale);
            BigDecimal outstanding = sale.getGrandTotal().subtract(paid);
            PaymentStatus status = statusFor(paid, sale.getGrandTotal());
            if ("PAID".equals(mode) && outstanding.compareTo(BigDecimal.ZERO) != 0) {
                continue;
            }
            if ("OUTSTANDING".equals(mode) && outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            rows.add(new OutstandingRowResponse(
                    sale.getCustomer() == null ? null : sale.getCustomer().getId().toString(),
                    sale.getCustomerName(),
                    sale.getId().toString(),
                    sale.getInvoiceNumber(),
                    sale.getSaleDate(),
                    sale.getGrandTotal(),
                    paid,
                    outstanding,
                    status));
        }
        rows.sort(Comparator.comparing(OutstandingRowResponse::saleDate).reversed());
        return rows;
    }

    @Transactional
    public PaymentResponse record(PaymentRequest request) {
        if (PipelineSupport.blankToNull(request.saleId()) != null) {
            return recordAgainstSale(PipelineSupport.parseUuid(request.saleId(), "saleId"), request);
        }
        if (PipelineSupport.blankToNull(request.salesOrderId()) != null) {
            return recordAgainstOrder(PipelineSupport.parseUuid(request.salesOrderId(), "salesOrderId"), request);
        }
        throw new RetailflowException(
                ErrorCodes.VALIDATION_ERROR, "A sale or sales order is required", HttpStatus.BAD_REQUEST.value());
    }

    public Payment recordPosCompletion(Sale sale, BigDecimal amount, PaymentMethod method, LocalDate date) {
        Tenant tenant = sale.getTenant();
        Payment payment = new Payment(
                UUID.randomUUID(),
                tenant,
                nextNumber(tenant),
                sale.getCustomer(),
                amount,
                method,
                date,
                TenantContext.require().userId());
        payment.setSale(sale);
        paymentRepository.save(payment);
        applySaleTotals(sale);
        return payment;
    }

    public void attachOrderPaymentsToSale(SalesOrder order, Sale sale) {
        for (Payment payment : paymentRepository.findBySalesOrderIdForUpdate(order.getId())) {
            if (payment.getSale() == null) {
                payment.setSale(sale);
            }
        }
        applySaleTotals(sale);
        order.applyPaid(paymentRepository.sumBySalesOrderId(order.getId()));
    }

    public BigDecimal paidForSale(Sale sale) {
        BigDecimal sum = paymentRepository.sumBySaleId(sale.getId());
        if (sum.compareTo(BigDecimal.ZERO) == 0
                && sale.getPaymentStatus() == PaymentStatus.PAID
                && sale.getSalesOrderId() == null) {
            // pre-M6 completed POS sales have no payment rows
            return sale.getGrandTotal();
        }
        return sum;
    }

    public void applySaleTotals(Sale sale) {
        BigDecimal paid = paymentRepository.sumBySaleId(sale.getId());
        PaymentStatus status = statusFor(paid, sale.getGrandTotal());
        PaymentMethod method = sale.getPaymentMethod();
        List<Payment> payments = paymentRepository.findBySaleIdOrderByPaymentDateAscCreatedAtAsc(sale.getId());
        if (!payments.isEmpty()) {
            method = payments.getLast().getPaymentMethod();
        }
        sale.applyPaymentState(status, method);
    }

    private PaymentResponse recordAgainstSale(UUID saleId, PaymentRequest request) {
        Sale sale = saleRepository
                .findByIdForUpdate(saleId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.SALE_NOT_FOUND, "Sale not found", HttpStatus.NOT_FOUND.value()));
        if (sale.getStatus() != in.retailflow.api.sales.domain.SaleStatus.COMPLETED) {
            throw new RetailflowException(
                    ErrorCodes.SALE_NOT_COMPLETED, "Record payments against a completed invoice", HttpStatus.CONFLICT.value());
        }
        BigDecimal paid = paymentRepository.sumBySaleId(sale.getId());
        BigDecimal outstanding = sale.getGrandTotal().subtract(paid);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        rejectIfExceeds(amount, outstanding);
        Payment payment = new Payment(
                UUID.randomUUID(),
                sale.getTenant(),
                nextNumber(sale.getTenant()),
                sale.getCustomer(),
                amount,
                request.paymentMethod(),
                request.paymentDate(),
                TenantContext.require().userId());
        payment.setSale(sale);
        if (sale.getSalesOrderId() != null) {
            salesOrderRepository.findById(sale.getSalesOrderId()).ifPresent(order -> {
                payment.setSalesOrder(order);
                order.applyPaid(paymentRepository.sumBySalesOrderId(order.getId()).add(amount));
            });
        }
        payment.setReferenceNumber(PipelineSupport.blankToNull(request.referenceNumber()));
        payment.setNotes(PipelineSupport.blankToNull(request.notes()));
        paymentRepository.save(payment);
        applySaleTotals(sale);
        notePayment(sale.getSalesOrderId() == null ? null : salesOrderRepository.findById(sale.getSalesOrderId()).orElse(null), amount);
        if (sale.getGrandTotal().subtract(paymentRepository.sumBySaleId(sale.getId())).compareTo(BigDecimal.ZERO) > 0) {
            notificationService.paymentOutstanding(
                    TenantContext.require().userId(),
                    "Payment outstanding",
                    sale.getInvoiceNumber() + " still has a balance",
                    sale.getId());
        }
        return toResponse(payment);
    }

    private PaymentResponse recordAgainstOrder(UUID orderId, PaymentRequest request) {
        SalesOrder order = salesOrderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.SALES_ORDER_NOT_FOUND, "Sales order not found", HttpStatus.NOT_FOUND.value()));
        if (order.getStatus() == in.retailflow.api.pipeline.domain.SalesOrderStatus.CANCELLED) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_STATUS, "Cannot collect payment on a cancelled order", HttpStatus.CONFLICT.value());
        }
        BigDecimal paid = paymentRepository.sumBySalesOrderId(order.getId());
        BigDecimal outstanding = order.getGrandTotal().subtract(paid);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        rejectIfExceeds(amount, outstanding);
        Payment payment = new Payment(
                UUID.randomUUID(),
                order.getTenant(),
                nextNumber(order.getTenant()),
                order.getCustomer(),
                amount,
                request.paymentMethod(),
                request.paymentDate(),
                TenantContext.require().userId());
        payment.setSalesOrder(order);
        if (order.getSale() != null) {
            payment.setSale(order.getSale());
        }
        payment.setReferenceNumber(PipelineSupport.blankToNull(request.referenceNumber()));
        payment.setNotes(PipelineSupport.blankToNull(request.notes()));
        paymentRepository.save(payment);
        order.applyPaid(paid.add(amount));
        if (order.getSale() != null) {
            applySaleTotals(order.getSale());
        }
        notePayment(order, amount);
        return toResponse(payment);
    }

    private void notePayment(SalesOrder order, BigDecimal amount) {
        if (order != null && order.getLead() != null) {
            leadService.record(
                    order.getLead(),
                    PipelineActivityType.PAYMENT_RECEIVED,
                    "Payment of ₹" + amount.toPlainString() + " received",
                    "PAYMENT",
                    order.getId());
        }
    }

    private void rejectIfExceeds(BigDecimal amount, BigDecimal outstanding) {
        if (amount.compareTo(outstanding) > 0) {
            throw new RetailflowException(
                    ErrorCodes.PAYMENT_EXCEEDS_OUTSTANDING,
                    "Payment cannot exceed the outstanding amount of ₹" + outstanding.toPlainString(),
                    HttpStatus.BAD_REQUEST.value());
        }
    }

    public static PaymentStatus statusFor(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return PaymentStatus.UNPAID;
        }
        if (paid.compareTo(total) >= 0) {
            return PaymentStatus.PAID;
        }
        return PaymentStatus.PARTIALLY_PAID;
    }

    private String nextNumber(Tenant tenant) {
        PaymentNumberCounter counter = counterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseGet(() -> {
                    try {
                        counterRepository.saveAndFlush(new PaymentNumberCounter(tenant));
                    } catch (DataIntegrityViolationException ignored) {
                    }
                    return counterRepository
                            .findByTenantIdForUpdate(tenant.getId())
                            .orElseThrow(() -> new RetailflowException(
                                    ErrorCodes.INTERNAL_ERROR,
                                    "Unable to allocate a payment number",
                                    HttpStatus.INTERNAL_SERVER_ERROR.value()));
                });
        return DocumentNumbers.format("PAY-", counter.nextValue());
    }

    private PaymentResponse toResponse(Payment payment) {
        String createdByName = userAccountRepository
                .findById(payment.getCreatedBy())
                .map(UserAccount::getFullName)
                .orElse("Staff");
        Sale sale = payment.getSale();
        SalesOrder order = payment.getSalesOrder();
        return new PaymentResponse(
                payment.getId().toString(),
                payment.getPaymentNumber(),
                sale == null ? null : sale.getId().toString(),
                sale == null ? null : sale.getInvoiceNumber(),
                order == null ? null : order.getId().toString(),
                order == null ? null : order.getOrderNumber(),
                payment.getCustomer() == null ? null : payment.getCustomer().getId().toString(),
                payment.getCustomer() == null ? null : payment.getCustomer().getName(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentDate(),
                payment.getReferenceNumber(),
                payment.getNotes(),
                payment.getCreatedBy().toString(),
                createdByName,
                payment.getCreatedAt());
    }
}
