package in.retailflow.api.security.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Component
public class TenantSessionBinder {

    @PersistenceContext
    private EntityManager entityManager;

    public void bindCurrentTenant(UUID tenantId) {
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.enableFilter("tenantFilter");
        filter.setParameter("tenantId", tenantId);
        session.doWork(connection -> {
            try (PreparedStatement tenant = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant_id', ?, true)")) {
                tenant.setString(1, tenantId.toString());
                tenant.execute();
            }
            try (PreparedStatement bypass = connection.prepareStatement(
                    "SELECT set_config('app.bypass_rls', ?, true)")) {
                bypass.setString(1, "off");
                bypass.execute();
            }
        });
    }

    /**
     * RLS bypass for {@link TenantBypass} authentication bootstrap only.
     * Hibernate tenant filter stays disabled so membership lookup is not tenant-filtered.
     * GUCs use {@code SET LOCAL} ({@code set_config(..., true)}) and do not leak to the next transaction.
     */
    public void enableRlsBypass() {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        session.doWork(connection -> {
            try (PreparedStatement bypass = connection.prepareStatement(
                    "SELECT set_config('app.bypass_rls', ?, true)")) {
                bypass.setString(1, "on");
                bypass.execute();
            }
        });
    }

    /**
     * No tenant on the thread and no bypass: empty tenant GUC, bypass off, filter disabled.
     * PostgreSQL policies then return no tenant-owned rows.
     */
    public void bindFailClosed() {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        session.doWork(connection -> {
            try (PreparedStatement tenant = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant_id', ?, true)")) {
                tenant.setString(1, "");
                tenant.execute();
            }
            try (PreparedStatement bypass = connection.prepareStatement(
                    "SELECT set_config('app.bypass_rls', ?, true)")) {
                bypass.setString(1, "off");
                bypass.execute();
            }
        });
    }
}
