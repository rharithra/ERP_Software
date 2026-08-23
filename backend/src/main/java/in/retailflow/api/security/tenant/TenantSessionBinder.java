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

    public void enableRlsBypass() {
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (PreparedStatement bypass = connection.prepareStatement(
                    "SELECT set_config('app.bypass_rls', ?, true)")) {
                bypass.setString(1, "on");
                bypass.execute();
            }
        });
    }
}
