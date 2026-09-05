package in.retailflow.api.tenant.domain;

import in.retailflow.api.common.audit.AuditedEntity;
import in.retailflow.api.identity.domain.MembershipStatus;
import in.retailflow.api.identity.domain.TenantRole;
import in.retailflow.api.identity.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "tenant_memberships")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class TenantMembership extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TenantRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    protected TenantMembership() {}

    public TenantMembership(UUID id, Tenant tenant, UserAccount user, TenantRole role) {
        this.id = id;
        this.tenant = tenant;
        this.user = user;
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public UserAccount getUser() {
        return user;
    }

    public TenantRole getRole() {
        return role;
    }

    public void setRole(TenantRole role) {
        this.role = role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }
}
