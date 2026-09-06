package in.retailflow.api.sales.domain;

import in.retailflow.api.common.audit.AuditedEntity;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "sale_returns")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class SaleReturn extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "return_number", nullable = false, length = 32)
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SaleReturnStatus status;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ReturnReason reason;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO.setScale(2);

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productName ASC")
    private List<SaleReturnItem> items = new ArrayList<>();

    protected SaleReturn() {}

    public SaleReturn(
            UUID id, Tenant tenant, String returnNumber, Sale sale, LocalDate returnDate, UUID createdBy) {
        this.id = id;
        this.tenant = tenant;
        this.returnNumber = returnNumber;
        this.sale = sale;
        this.customer = sale.getCustomer();
        this.status = SaleReturnStatus.DRAFT;
        this.returnDate = returnDate;
        this.createdBy = createdBy;
    }

    public void replaceItems(List<SaleReturnItem> next, BigDecimal headerDiscount) {
        items.clear();
        for (SaleReturnItem item : next) {
            item.setSaleReturn(this);
            items.add(item);
        }
        this.discount = headerDiscount == null ? BigDecimal.ZERO.setScale(2) : headerDiscount;
        recalculate();
    }

    public void recalculate() {
        BigDecimal sub = BigDecimal.ZERO.setScale(2);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);
        BigDecimal lines = BigDecimal.ZERO.setScale(2);
        for (SaleReturnItem item : items) {
            sub = sub.add(item.getTaxableAmount());
            tax = tax.add(item.getTaxAmount());
            lines = lines.add(item.getTotalAmount());
        }
        this.subtotal = sub;
        this.taxAmount = tax;
        BigDecimal disc = discount == null ? BigDecimal.ZERO.setScale(2) : discount;
        this.totalAmount = lines.subtract(disc).max(BigDecimal.ZERO.setScale(2));
    }

    public void markCompleted(UUID actor, Instant at) {
        requireDraft();
        if (items.isEmpty()) {
            throw new RetailflowException(
                    ErrorCodes.RETURN_HAS_NO_ITEMS, "Add at least one item before completing this return", HttpStatus.BAD_REQUEST.value());
        }
        this.status = SaleReturnStatus.COMPLETED;
        this.completedAt = at;
        this.completedBy = actor;
    }

    public void markCancelled() {
        requireDraft();
        this.status = SaleReturnStatus.CANCELLED;
    }

    public void requireDraft() {
        if (status == SaleReturnStatus.COMPLETED) {
            throw new RetailflowException(
                    ErrorCodes.RETURN_ALREADY_COMPLETED, "This return is already completed", HttpStatus.CONFLICT.value());
        }
        if (status == SaleReturnStatus.CANCELLED) {
            throw new RetailflowException(
                    ErrorCodes.RETURN_ALREADY_CANCELLED, "This return is cancelled", HttpStatus.CONFLICT.value());
        }
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public Sale getSale() {
        return sale;
    }

    public Customer getCustomer() {
        return customer;
    }

    public SaleReturnStatus getStatus() {
        return status;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public ReturnReason getReason() {
        return reason;
    }

    public void setReason(ReturnReason reason) {
        this.reason = reason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
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

    public List<SaleReturnItem> getItems() {
        return items;
    }
}
