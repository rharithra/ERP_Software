package in.retailflow.api.sales.repository;

import in.retailflow.api.sales.domain.PaymentNumberCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentNumberCounterRepository extends JpaRepository<PaymentNumberCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PaymentNumberCounter c WHERE c.tenantId = :tenantId")
    Optional<PaymentNumberCounter> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
