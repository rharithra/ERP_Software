package in.retailflow.api.sales.domain;

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

@Entity
@Table(name = "customer_credit_transactions")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class CustomerCreditTransaction {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    private CreditTransactionType transactionType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_sale_id")
    private Sale sourceSale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_return_id")
    private SaleReturn saleReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id")
    private Refund refund;

    @Column(length = 200)
    private String reference;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CustomerCreditTransaction() {}

    public CustomerCreditTransaction(
            UUID id,
            Tenant tenant,
            Customer customer,
            CreditTransactionType type,
            BigDecimal amount,
            UUID createdBy,
            String reference) {
        this.id = id;
        this.tenant = tenant;
        this.customer = customer;
        this.transactionType = type;
        this.amount = amount;
        this.createdBy = createdBy;
        this.reference = reference;
    }

    public void setSale(Sale sale) {
        this.sale = sale;
    }

    public void setSourceSale(Sale sourceSale) {
        this.sourceSale = sourceSale;
    }

    public void setSaleReturn(SaleReturn saleReturn) {
        this.saleReturn = saleReturn;
    }

    public void setRefund(Refund refund) {
        this.refund = refund;
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public CreditTransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Sale getSale() {
        return sale;
    }

    public Sale getSourceSale() {
        return sourceSale;
    }

    public SaleReturn getSaleReturn() {
        return saleReturn;
    }

    public Refund getRefund() {
        return refund;
    }

    public String getReference() {
        return reference;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
