package in.retailflow.api.procurement.domain;

import in.retailflow.api.common.audit.AuditedEntity;
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
import org.hibernate.annotations.Formula;

@Entity
@Table(name = "purchases")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Purchase extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "purchase_number", nullable = false, length = 32)
    private String purchaseNumber;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PurchaseStatus status;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 1000)
    private String notes;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sku ASC")
    private List<PurchaseItem> items = new ArrayList<>();

    @Formula("(SELECT COUNT(*) FROM purchase_items i WHERE i.purchase_id = id)")
    private int itemCount;

    protected Purchase() {}

    public Purchase(
            UUID id,
            Tenant tenant,
            Supplier supplier,
            String purchaseNumber,
            LocalDate purchaseDate,
            String notes,
            UUID createdBy) {
        this.id = id;
        this.tenant = tenant;
        this.supplier = supplier;
        this.purchaseNumber = purchaseNumber;
        this.purchaseDate = purchaseDate;
        this.status = PurchaseStatus.DRAFT;
        this.subtotal = BigDecimal.ZERO.setScale(2);
        this.taxAmount = BigDecimal.ZERO.setScale(2);
        this.totalAmount = BigDecimal.ZERO.setScale(2);
        this.notes = notes;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public String getPurchaseNumber() {
        return purchaseNumber;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public List<PurchaseItem> getItems() {
        return items;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void replaceItems(List<PurchaseItem> next) {
        items.clear();
        for (PurchaseItem item : next) {
            item.setPurchase(this);
            items.add(item);
        }
        recalculateTotals();
    }

    public void recalculateTotals() {
        BigDecimal sub = BigDecimal.ZERO.setScale(2);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);
        for (PurchaseItem item : items) {
            sub = sub.add(item.getLineSubtotal());
            tax = tax.add(item.getTaxAmount());
        }
        this.subtotal = sub;
        this.taxAmount = tax;
        this.totalAmount = sub.add(tax);
    }

    public void markReceived(Instant at) {
        this.status = PurchaseStatus.RECEIVED;
        this.receivedAt = at;
    }

    public void markCancelled() {
        this.status = PurchaseStatus.CANCELLED;
    }
}
