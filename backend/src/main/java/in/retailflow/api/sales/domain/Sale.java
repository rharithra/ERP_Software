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
import org.hibernate.annotations.Formula;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "sales")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Sale extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "sale_number", nullable = false, length = 32)
    private String saleNumber;

    @Column(name = "invoice_number", length = 32)
    private String invoiceNumber;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SaleStatus status;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 16)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 16)
    private PaymentStatus paymentStatus;

    @Column(length = 1000)
    private String notes;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "customer_gstin", length = 15)
    private String customerGstin;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "company_gstin", length = 15)
    private String companyGstin;

    @Column(name = "company_address", length = 500)
    private String companyAddress;

    @Column(name = "company_phone", length = 20)
    private String companyPhone;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "sales_order_id")
    private UUID salesOrderId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sku ASC")
    private List<SaleItem> items = new ArrayList<>();

    @Formula("(SELECT COUNT(*) FROM sale_items i WHERE i.sale_id = id)")
    private int itemCount;

    protected Sale() {}

    public Sale(UUID id, Tenant tenant, String saleNumber, LocalDate saleDate, String notes, UUID createdBy) {
        this.id = id;
        this.tenant = tenant;
        this.saleNumber = saleNumber;
        this.saleDate = saleDate;
        this.status = SaleStatus.DRAFT;
        this.subtotal = BigDecimal.ZERO.setScale(2);
        this.discount = BigDecimal.ZERO.setScale(2);
        this.taxTotal = BigDecimal.ZERO.setScale(2);
        this.grandTotal = BigDecimal.ZERO.setScale(2);
        this.notes = notes;
        this.customerName = "Walk-in";
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getSaleNumber() {
        return saleNumber;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public void setSaleDate(LocalDate saleDate) {
        this.saleDate = saleDate;
    }

    public SaleStatus getStatus() {
        return status;
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

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getCustomerGstin() {
        return customerGstin;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getCompanyGstin() {
        return companyGstin;
    }

    public String getCompanyAddress() {
        return companyAddress;
    }

    public String getCompanyPhone() {
        return companyPhone;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getSalesOrderId() {
        return salesOrderId;
    }

    public void setSalesOrderId(UUID salesOrderId) {
        this.salesOrderId = salesOrderId;
    }

    public void applyPaymentState(PaymentStatus paymentStatus, PaymentMethod paymentMethod) {
        this.paymentStatus = paymentStatus;
        if (paymentMethod != null) {
            this.paymentMethod = paymentMethod;
        }
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public List<SaleItem> getItems() {
        return items;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void assignCustomer(Customer customer) {
        this.customer = customer;
        if (customer == null) {
            this.customerName = "Walk-in";
            this.customerPhone = null;
            this.customerGstin = null;
            return;
        }
        this.customerName = customer.getName();
        this.customerPhone = customer.getPhone();
        this.customerGstin = customer.getGstin();
    }

    public void replaceItems(List<SaleItem> next, BigDecimal saleDiscount) {
        items.clear();
        for (SaleItem item : next) {
            item.setSale(this);
            items.add(item);
        }
        this.discount = saleDiscount == null ? BigDecimal.ZERO.setScale(2) : saleDiscount.setScale(2);
        recalculateTotals();
    }

    public void recalculateTotals() {
        BigDecimal sub = BigDecimal.ZERO.setScale(2);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);
        for (SaleItem item : items) {
            sub = sub.add(item.getTaxableAmount());
            tax = tax.add(item.getTaxAmount());
        }
        this.subtotal = sub;
        this.taxTotal = tax;
        if (discount.compareTo(sub) > 0) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR,
                    "Discount cannot exceed the sale subtotal",
                    HttpStatus.BAD_REQUEST.value());
        }
        this.grandTotal = sub.subtract(discount).add(tax);
    }

    public void markCompleted(
            String invoiceNumber,
            PaymentMethod paymentMethod,
            Instant at,
            String companyName,
            String companyGstin,
            String companyAddress,
            String companyPhone) {
        this.status = SaleStatus.COMPLETED;
        this.invoiceNumber = invoiceNumber;
        this.paymentMethod = paymentMethod;
        this.completedAt = at;
        this.companyName = companyName;
        this.companyGstin = companyGstin;
        this.companyAddress = companyAddress;
        this.companyPhone = companyPhone;
    }

    public void markCancelled() {
        this.status = SaleStatus.CANCELLED;
    }
}
