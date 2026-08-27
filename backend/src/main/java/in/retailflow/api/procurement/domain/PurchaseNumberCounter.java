package in.retailflow.api.procurement.domain;

import in.retailflow.api.tenant.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "purchase_number_counters")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PurchaseNumberCounter {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "last_value", nullable = false)
    private long lastValue;

    protected PurchaseNumberCounter() {}

    public PurchaseNumberCounter(Tenant tenant) {
        this.tenantId = tenant.getId();
        this.lastValue = 0;
    }

    public long nextValue() {
        lastValue += 1;
        return lastValue;
    }
}
