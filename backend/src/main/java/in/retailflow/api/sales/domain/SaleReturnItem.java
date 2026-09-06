package in.retailflow.api.sales.domain;

import in.retailflow.api.catalog.domain.Product;
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
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "sale_return_items")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class SaleReturnItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_return_id", nullable = false)
    private SaleReturn saleReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false)
    private SaleItem saleItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(length = 64)
    private String barcode;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "gst_rate", nullable = false, precision = 4, scale = 2)
    private BigDecimal gstRate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount;

    @Column(name = "taxable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ReturnReason reason;

    protected SaleReturnItem() {}

    public SaleReturnItem(
            UUID id,
            Tenant tenant,
            SaleItem saleItem,
            BigDecimal quantity,
            BigDecimal discount,
            SaleMoney.LineTotals totals,
            ReturnReason reason) {
        this.id = id;
        this.tenant = tenant;
        this.saleItem = saleItem;
        this.product = saleItem.getProduct();
        this.productName = saleItem.getProductName();
        this.sku = saleItem.getSku();
        this.barcode = saleItem.getProduct().getBarcode();
        this.unit = saleItem.getUnit();
        this.quantity = quantity;
        this.unitPrice = saleItem.getUnitPrice();
        this.gstRate = saleItem.getGstRate();
        this.discount = discount;
        this.taxableAmount = totals.taxableAmount();
        this.taxAmount = totals.taxAmount();
        this.totalAmount = totals.total();
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public SaleItem getSaleItem() {
        return saleItem;
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

    public String getBarcode() {
        return barcode;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getGstRate() {
        return gstRate;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public ReturnReason getReason() {
        return reason;
    }

    void setSaleReturn(SaleReturn saleReturn) {
        this.saleReturn = saleReturn;
    }
}
