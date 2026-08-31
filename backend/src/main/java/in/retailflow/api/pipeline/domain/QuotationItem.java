package in.retailflow.api.pipeline.domain;

import in.retailflow.api.catalog.domain.Product;
import in.retailflow.api.sales.domain.SaleMoney;
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
@Table(name = "quotation_items")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class QuotationItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "sku_snapshot", nullable = false, length = 64)
    private String skuSnapshot;

    @Column(name = "unit_snapshot", nullable = false, length = 16)
    private String unitSnapshot;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount;

    @Column(name = "gst_rate", nullable = false, precision = 4, scale = 2)
    private BigDecimal gstRate;

    @Column(name = "taxable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    protected QuotationItem() {}

    public QuotationItem(
            UUID id,
            Tenant tenant,
            Quotation quotation,
            Product product,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal gstRate,
            BigDecimal discount,
            SaleMoney.LineTotals totals) {
        this.id = id;
        this.tenant = tenant;
        this.quotation = quotation;
        this.product = product;
        this.productNameSnapshot = product.getName();
        this.skuSnapshot = product.getSku();
        this.unitSnapshot = product.getUnit().name();
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.gstRate = gstRate;
        this.discount = discount;
        this.taxableAmount = totals.taxableAmount();
        this.taxAmount = totals.taxAmount();
        this.lineTotal = totals.total();
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public String getSkuSnapshot() {
        return skuSnapshot;
    }

    public String getUnitSnapshot() {
        return unitSnapshot;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getGstRate() {
        return gstRate;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    void setQuotation(Quotation quotation) {
        this.quotation = quotation;
    }
}
