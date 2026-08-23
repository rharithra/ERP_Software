package in.retailflow.api.security.tenant;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

@Configuration
public class TenantTransactionConfiguration {

    @Bean
    TransactionManagerCustomizer<AbstractPlatformTransactionManager> tenantTransactionManagerCustomizer(
            TenantTransactionListener tenantTransactionListener) {
        return manager -> {
            List<TransactionExecutionListener> listeners =
                    new ArrayList<>(manager.getTransactionExecutionListeners());
            if (!listeners.contains(tenantTransactionListener)) {
                listeners.add(tenantTransactionListener);
                manager.setTransactionExecutionListeners(listeners);
            }
        };
    }
}
