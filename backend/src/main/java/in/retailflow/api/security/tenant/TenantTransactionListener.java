package in.retailflow.api.security.tenant;

import java.util.UUID;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Binds Hibernate tenant filtering and PostgreSQL RLS GUCs at the start of every
 * new transaction. Bypass is available only while {@link TenantBypass} is active
 * on the calling thread (set before the transaction begins).
 */
@Component
public class TenantTransactionListener implements TransactionExecutionListener {

    private final TenantSessionBinder tenantSessionBinder;

    public TenantTransactionListener(TenantSessionBinder tenantSessionBinder) {
        this.tenantSessionBinder = tenantSessionBinder;
    }

    @Override
    public void afterBegin(TransactionExecution transaction, @Nullable Throwable beginFailure) {
        if (beginFailure != null || !transaction.isNewTransaction()) {
            return;
        }
        try {
            if (TenantBypassHolder.isBypass()) {
                tenantSessionBinder.enableRlsBypass();
                return;
            }
            UUID tenantId = TenantContext.getTenantId();
            if (tenantId != null) {
                tenantSessionBinder.bindCurrentTenant(tenantId);
                return;
            }
            tenantSessionBinder.bindFailClosed();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to bind tenant session to the transaction", ex);
        }
    }
}
