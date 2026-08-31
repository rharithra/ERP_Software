package in.retailflow.api.pipeline.domain;

import in.retailflow.api.common.audit.AuditedEntity;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.sales.domain.Customer;
import in.retailflow.api.sales.domain.SaleMoney;
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
@Table(name = "quotations")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Quotation extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "quotation_number", nullable = false, length = 32)
    private String quotationNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "quotation_date", nullable = false)
    private LocalDate quotationDate;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuotationStatus status;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal;

    @Column(length = 2000)
    private String notes;

    @Column(name = "terms_and_conditions", length = 4000)
    private String termsAndConditions;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("skuSnapshot ASC")
    private List<QuotationItem> items = new ArrayList<>();

    protected Quotation() {}

    public Quotation(
            UUID id,
            Tenant tenant,
            String quotationNumber,
            Customer customer,
            LocalDate quotationDate,
            LocalDate validUntil,
            UUID createdBy) {
        this.id = id;
        this.tenant = tenant;
        this.quotationNumber = quotationNumber;
        this.customer = customer;
        this.quotationDate = quotationDate;
        this.validUntil = validUntil;
        this.status = QuotationStatus.DRAFT;
        this.subtotal = BigDecimal.ZERO.setScale(2);
        this.discount = BigDecimal.ZERO.setScale(2);
        this.taxTotal = BigDecimal.ZERO.setScale(2);
        this.grandTotal = BigDecimal.ZERO.setScale(2);
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getQuotationNumber() {
        return quotationNumber;
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

    public LocalDate getQuotationDate() {
        return quotationDate;
    }

    public void setQuotationDate(LocalDate quotationDate) {
        this.quotationDate = quotationDate;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public QuotationStatus getStatus() {
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getTermsAndConditions() {
        return termsAndConditions;
    }

    public void setTermsAndConditions(String termsAndConditions) {
        this.termsAndConditions = termsAndConditions;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public List<QuotationItem> getItems() {
        return items;
    }

    public void replaceItems(List<QuotationItem> next, BigDecimal headerDiscount) {
        requireDraft();
        items.clear();
        for (QuotationItem item : next) {
            item.setQuotation(this);
            items.add(item);
        }
        this.discount = headerDiscount == null ? BigDecimal.ZERO.setScale(2) : headerDiscount.setScale(2);
        recalculateTotals();
    }

    public void recalculateTotals() {
        BigDecimal sub = BigDecimal.ZERO.setScale(2);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);
        for (QuotationItem item : items) {
            sub = sub.add(item.getTaxableAmount());
            tax = tax.add(item.getTaxAmount());
        }
        this.subtotal = sub;
        this.taxTotal = tax;
        if (discount.compareTo(sub) > 0) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR, "Discount cannot exceed the quotation subtotal", HttpStatus.BAD_REQUEST.value());
        }
        this.grandTotal = sub.subtract(discount).add(tax);
    }

    public void requireDraft() {
        if (status != QuotationStatus.DRAFT) {
            throw new RetailflowException(
                    ErrorCodes.QUOTATION_NOT_EDITABLE, "Only draft quotations can be edited", HttpStatus.CONFLICT.value());
        }
    }

    public void markSent() {
        requireDraft();
        if (items.isEmpty()) {
            throw new RetailflowException(ErrorCodes.VALIDATION_ERROR, "Add at least one product", HttpStatus.BAD_REQUEST.value());
        }
        this.status = QuotationStatus.SENT;
    }

    public void markAccepted() {
        if (status == QuotationStatus.EXPIRED) {
            throw new RetailflowException(ErrorCodes.QUOTATION_EXPIRED, "This quotation has expired", HttpStatus.CONFLICT.value());
        }
        if (status != QuotationStatus.SENT) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_STATUS, "Only sent quotations can be accepted", HttpStatus.CONFLICT.value());
        }
        this.status = QuotationStatus.ACCEPTED;
    }

    public void markRejected() {
        if (status != QuotationStatus.SENT) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_STATUS, "Only sent quotations can be rejected", HttpStatus.CONFLICT.value());
        }
        this.status = QuotationStatus.REJECTED;
    }

    public void markCancelled() {
        if (status == QuotationStatus.ACCEPTED) {
            throw new RetailflowException(
                    ErrorCodes.QUOTATION_NOT_EDITABLE, "Accepted quotations cannot be cancelled", HttpStatus.CONFLICT.value());
        }
        if (status == QuotationStatus.CANCELLED || status == QuotationStatus.EXPIRED) {
            throw new RetailflowException(ErrorCodes.INVALID_STATUS, "This quotation is already closed", HttpStatus.CONFLICT.value());
        }
        this.status = QuotationStatus.CANCELLED;
    }

    public void expireIfNeeded(LocalDate today) {
        if (status == QuotationStatus.SENT && validUntil.isBefore(today)) {
            this.status = QuotationStatus.EXPIRED;
        }
    }

    public static SaleMoney.LineTotals lineTotals(
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal gst, BigDecimal discount) {
        return SaleMoney.line(quantity, unitPrice, gst, discount);
    }
}
