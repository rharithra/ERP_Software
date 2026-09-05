package in.retailflow.api.identity.domain;

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
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "user_management_events")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class UserManagementEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private UserManagementEventType eventType;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserManagementEvent() {}

    public UserManagementEvent(
            UUID id,
            Tenant tenant,
            UUID actorUserId,
            UUID targetUserId,
            UserManagementEventType eventType,
            String message) {
        this.id = id;
        this.tenant = tenant;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.eventType = eventType;
        this.message = message;
    }

    public UUID getId() {
        return id;
    }

    public UserManagementEventType getEventType() {
        return eventType;
    }

    public String getMessage() {
        return message;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
