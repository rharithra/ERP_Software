package in.retailflow.api.sales.repository;

import in.retailflow.api.sales.domain.InvoiceNumberCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceNumberCounterRepository extends JpaRepository<InvoiceNumberCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM InvoiceNumberCounter c WHERE c.tenantId = :tenantId")
    Optional<InvoiceNumberCounter> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
