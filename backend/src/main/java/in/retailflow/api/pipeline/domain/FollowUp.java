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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "follow_ups")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class FollowUp extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FollowUpType type;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FollowUpStatus status;

    @Column(length = 2000)
    private String notes;

    @Column(length = 2000)
    private String outcome;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected FollowUp() {}

    public FollowUp(UUID id, Tenant tenant, Lead lead, FollowUpType type, LocalDate dueDate) {
        this.id = id;
        this.tenant = tenant;
        this.lead = lead;
        this.type = type;
        this.dueDate = dueDate;
        this.status = FollowUpStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Lead getLead() {
        return lead;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public FollowUpType getType() {
        return type;
    }

    public void setType(FollowUpType type) {
        this.type = type;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalTime dueTime) {
        this.dueTime = dueTime;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(UUID assignedTo) {
        this.assignedTo = assignedTo;
    }

    public FollowUpStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isPending() {
        return status == FollowUpStatus.PENDING;
    }

    public void requirePending() {
        if (!isPending()) {
            throw new in.retailflow.api.common.exception.RetailflowException(
                    in.retailflow.api.common.exception.ErrorCodes.INVALID_STATUS,
                    "Only pending follow-ups can be changed",
                    org.springframework.http.HttpStatus.CONFLICT.value());
        }
    }

    public void complete(String outcome) {
        requirePending();
        this.status = FollowUpStatus.COMPLETED;
        this.outcome = outcome;
        this.completedAt = Instant.now();
    }

    public void cancel(String outcome) {
        requirePending();
        this.status = FollowUpStatus.CANCELLED;
        this.outcome = outcome;
    }
}
