package in.retailflow.api.inventory.domain;

import in.retailflow.api.catalog.domain.Product;
import in.retailflow.api.common.audit.AuditedEntity;
import in.retailflow.api.tenant.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "inventory_balances")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class InventoryBalance extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "reorder_level", nullable = false, precision = 19, scale = 3)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "opening_recorded", nullable = false)
    private boolean openingRecorded;

    protected InventoryBalance() {}

    public InventoryBalance(UUID id, Tenant tenant, Product product) {
        this.id = id;
        this.tenant = tenant;
        this.product = product;
        this.quantity = BigDecimal.ZERO.setScale(3);
        this.reorderLevel = BigDecimal.ZERO.setScale(3);
        this.openingRecorded = false;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(BigDecimal reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public boolean isOpeningRecorded() {
        return openingRecorded;
    }

    public void setOpeningRecorded(boolean openingRecorded) {
        this.openingRecorded = openingRecorded;
    }

    public InventoryStatus status() {
        return InventoryStatuses.from(quantity, reorderLevel);
    }
}
