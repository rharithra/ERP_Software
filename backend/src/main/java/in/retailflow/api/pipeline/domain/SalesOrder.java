package in.retailflow.api.pipeline.domain;

import in.retailflow.api.common.audit.AuditedEntity;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.sales.domain.Customer;
import in.retailflow.api.sales.domain.PaymentStatus;
import in.retailflow.api.sales.domain.Sale;
import in.retailflow.api.tenant.domain.Tenant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "sales_orders")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class SalesOrder extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "order_number", nullable = false, length = 32)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SalesOrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    private PaymentStatus paymentStatus;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "advance_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal advancePaid;

    @Column(name = "outstanding_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstandingAmount;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("skuSnapshot ASC")
    private List<SalesOrderItem> items = new ArrayList<>();

    protected SalesOrder() {}

    public SalesOrder(
            UUID id,
            Tenant tenant,
            String orderNumber,
            Customer customer,
            LocalDate orderDate,
            UUID createdBy) {
        this.id = id;
        this.tenant = tenant;
        this.orderNumber = orderNumber;
        this.customer = customer;
        this.orderDate = orderDate;
        this.status = SalesOrderStatus.DRAFT;
        this.paymentStatus = PaymentStatus.UNPAID;
        this.subtotal = BigDecimal.ZERO.setScale(2);
        this.discount = BigDecimal.ZERO.setScale(2);
        this.taxTotal = BigDecimal.ZERO.setScale(2);
        this.grandTotal = BigDecimal.ZERO.setScale(2);
        this.advancePaid = BigDecimal.ZERO.setScale(2);
        this.outstandingAmount = BigDecimal.ZERO.setScale(2);
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Quotation getQuotation() {
        return quotation;
    }

    public void setQuotation(Quotation quotation) {
        this.quotation = quotation;
    }

    public Lead getLead() {
        return lead;
    }

    public void setLead(Lead lead) {
        this.lead = lead;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Sale getSale() {
        return sale;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public LocalDate getExpectedDeliveryDate() {
        return expectedDeliveryDate;
    }

    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) {
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    public SalesOrderStatus getStatus() {
        return status;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public BigDecimal getAdvancePaid() {
        return advancePaid;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public List<SalesOrderItem> getItems() {
        return items;
    }

    public void replaceItems(List<SalesOrderItem> next, BigDecimal headerDiscount) {
        requireDraft();
        items.clear();
        for (SalesOrderItem item : next) {
            item.setSalesOrder(this);
            items.add(item);
        }
        this.discount = headerDiscount == null ? BigDecimal.ZERO.setScale(2) : headerDiscount.setScale(2);
        recalculateTotals();
        applyPaid(this.advancePaid);
    }

    public void recalculateTotals() {
        BigDecimal sub = BigDecimal.ZERO.setScale(2);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);
        for (SalesOrderItem item : items) {
            sub = sub.add(item.getTaxableAmount());
            tax = tax.add(item.getTaxAmount());
        }
        this.subtotal = sub;
        this.taxTotal = tax;
        if (discount.compareTo(sub) > 0) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR, "Discount cannot exceed the order subtotal", HttpStatus.BAD_REQUEST.value());
        }
        this.grandTotal = sub.subtract(discount).add(tax);
        applyPaid(this.advancePaid);
    }

    public void applyPaid(BigDecimal paid) {
        this.advancePaid = paid == null ? BigDecimal.ZERO.setScale(2) : paid.setScale(2);
        this.outstandingAmount = this.grandTotal.subtract(this.advancePaid);
        if (outstandingAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RetailflowException(
                    ErrorCodes.PAYMENT_EXCEEDS_OUTSTANDING,
                    "Payment cannot exceed the outstanding amount",
                    HttpStatus.BAD_REQUEST.value());
        }
        if (advancePaid.compareTo(BigDecimal.ZERO) == 0) {
            this.paymentStatus = PaymentStatus.UNPAID;
        } else if (outstandingAmount.compareTo(BigDecimal.ZERO) == 0) {
            this.paymentStatus = PaymentStatus.PAID;
        } else {
            this.paymentStatus = PaymentStatus.PARTIALLY_PAID;
        }
    }

    public void requireDraft() {
        if (status != SalesOrderStatus.DRAFT) {
            throw new RetailflowException(
                    ErrorCodes.SALES_ORDER_NOT_EDITABLE, "Only draft sales orders can be edited", HttpStatus.CONFLICT.value());
        }
    }

    public void confirm() {
        requireDraft();
        if (items.isEmpty()) {
            throw new RetailflowException(ErrorCodes.VALIDATION_ERROR, "Add at least one product", HttpStatus.BAD_REQUEST.value());
        }
        this.status = SalesOrderStatus.CONFIRMED;
    }

    public void process() {
        if (status != SalesOrderStatus.CONFIRMED && status != SalesOrderStatus.PROCESSING) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_STATUS, "Confirm the sales order before processing", HttpStatus.CONFLICT.value());
        }
        this.status = SalesOrderStatus.PROCESSING;
    }

    public void markReady() {
        if (status != SalesOrderStatus.PROCESSING && status != SalesOrderStatus.CONFIRMED) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_STATUS, "This sales order is not ready to be marked ready", HttpStatus.CONFLICT.value());
        }
        this.status = SalesOrderStatus.READY;
    }

    public void requireConvertible() {
        if (status != SalesOrderStatus.CONFIRMED
                && status != SalesOrderStatus.PROCESSING
                && status != SalesOrderStatus.READY) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_STATUS,
                    "Confirm the sales order before converting it to a sale",
                    HttpStatus.CONFLICT.value());
        }
        if (sale != null) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_STATUS, "This sales order is already converted to a sale", HttpStatus.CONFLICT.value());
        }
    }

    public void markCompleted(Sale sale) {
        this.sale = sale;
        this.status = SalesOrderStatus.COMPLETED;
    }

    public void cancel() {
        if (status == SalesOrderStatus.COMPLETED) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_STATUS, "A completed sales order cannot be cancelled", HttpStatus.CONFLICT.value());
        }
        if (status == SalesOrderStatus.CANCELLED) {
            throw new RetailflowException(ErrorCodes.INVALID_STATUS, "This sales order is already cancelled", HttpStatus.CONFLICT.value());
        }
        this.status = SalesOrderStatus.CANCELLED;
    }
}
