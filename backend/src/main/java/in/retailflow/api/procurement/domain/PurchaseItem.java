package in.retailflow.api.procurement.domain;

import in.retailflow.api.catalog.domain.Product;
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
@Table(name = "purchase_items")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PurchaseItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "gst_rate", nullable = false, precision = 4, scale = 2)
    private BigDecimal gstRate;

    @Column(name = "line_subtotal", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineSubtotal;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    protected PurchaseItem() {}

    public PurchaseItem(
            UUID id,
            Tenant tenant,
            Purchase purchase,
            Product product,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal gstRate,
            PurchaseMoney.LineTotals totals) {
        this.id = id;
        this.tenant = tenant;
        this.purchase = purchase;
        this.product = product;
        this.productName = product.getName();
        this.sku = product.getSku();
        this.unit = product.getUnit().name();
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.gstRate = gstRate;
        this.lineSubtotal = totals.subtotal();
        this.taxAmount = totals.taxAmount();
        this.lineTotal = totals.total();
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getProductName() {
        return productName;
    }

    public String getSku() {
        return sku;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getGstRate() {
        return gstRate;
    }

    public BigDecimal getLineSubtotal() {
        return lineSubtotal;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    void setPurchase(Purchase purchase) {
        this.purchase = purchase;
    }
}
