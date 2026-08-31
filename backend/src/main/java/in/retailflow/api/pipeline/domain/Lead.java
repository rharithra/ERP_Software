package in.retailflow.api.pipeline.domain;

import in.retailflow.api.common.audit.AuditedEntity;
import in.retailflow.api.sales.domain.Customer;
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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "leads")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Lead extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "lead_number", nullable = false, length = 32)
    private String leadNumber;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 320)
    private String email;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(length = 500)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LeadSource source;

    @Column(length = 2000)
    private String requirement;

    @Column(name = "expected_value", precision = 14, scale = 2)
    private BigDecimal expectedValue;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeadPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeadStatus status;

    @Column(length = 2000)
    private String notes;

    @Column(name = "lost_reason", length = 1000)
    private String lostReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_customer_id")
    private Customer convertedCustomer;

    protected Lead() {}

    public Lead(UUID id, Tenant tenant, String leadNumber, String name) {
        this.id = id;
        this.tenant = tenant;
        this.leadNumber = leadNumber;
        this.name = name;
        this.source = LeadSource.WALK_IN;
        this.priority = LeadPriority.MEDIUM;
        this.status = LeadStatus.NEW;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getLeadNumber() {
        return leadNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LeadSource getSource() {
        return source;
    }

    public void setSource(LeadSource source) {
        this.source = source;
    }

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public BigDecimal getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(BigDecimal expectedValue) {
        this.expectedValue = expectedValue;
    }

    public LocalDate getExpectedCloseDate() {
        return expectedCloseDate;
    }

    public void setExpectedCloseDate(LocalDate expectedCloseDate) {
        this.expectedCloseDate = expectedCloseDate;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(UUID assignedTo) {
        this.assignedTo = assignedTo;
    }

    public LeadPriority getPriority() {
        return priority;
    }

    public void setPriority(LeadPriority priority) {
        this.priority = priority == null ? LeadPriority.MEDIUM : priority;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getLostReason() {
        return lostReason;
    }

    public Customer getConvertedCustomer() {
        return convertedCustomer;
    }

    public void changeStatus(LeadStatus next, String lostReason) {
        status.requireTransition(next);
        this.status = next;
        this.lostReason = next == LeadStatus.LOST ? lostReason : null;
    }

    public void markConverted(Customer customer) {
        this.convertedCustomer = customer;
        if (status != LeadStatus.WON && status != LeadStatus.LOST) {
            // conversion does not force WON; sale completion does
        }
    }

    public void advanceAtLeast(LeadStatus floor) {
        if (status == LeadStatus.WON || status == LeadStatus.LOST) {
            return;
        }
        if (status.ordinal() < floor.ordinal()) {
            this.status = floor;
        }
    }
}
