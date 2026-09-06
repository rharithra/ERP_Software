package in.retailflow.api.sales.domain;

import in.retailflow.api.common.audit.AuditedEntity;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.tenant.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "refunds")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Refund extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "refund_number", nullable = false, length = 32)
    private String refundNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_return_id")
    private SaleReturn saleReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RefundStatus status;

    @Column(name = "reference_number", length = 64)
    private String referenceNumber;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    protected Refund() {}

    public Refund(
            UUID id,
            Tenant tenant,
            String refundNumber,
            Sale sale,
            SaleReturn saleReturn,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            UUID createdBy) {
        this.id = id;
        this.tenant = tenant;
        this.refundNumber = refundNumber;
        this.sale = sale;
        this.saleReturn = saleReturn;
        this.customer = sale.getCustomer();
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = RefundStatus.PENDING;
        this.createdBy = createdBy;
    }

    public void markCompleted(UUID actor, Instant at) {
        requirePending();
        this.status = RefundStatus.COMPLETED;
        this.completedAt = at;
        this.completedBy = actor;
    }

    public void markCancelled() {
        requirePending();
        this.status = RefundStatus.CANCELLED;
    }

    public void requirePending() {
        if (status == RefundStatus.COMPLETED) {
            throw new RetailflowException(
                    ErrorCodes.REFUND_ALREADY_COMPLETED, "This refund is already completed", HttpStatus.CONFLICT.value());
        }
        if (status == RefundStatus.CANCELLED) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_REFUND_STATUS, "This refund is cancelled", HttpStatus.CONFLICT.value());
        }
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getRefundNumber() {
        return refundNumber;
    }

    public Sale getSale() {
        return sale;
    }

    public SaleReturn getSaleReturn() {
        return saleReturn;
    }

    public Customer getCustomer() {
        return customer;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }
}
