package in.retailflow.api.procurement.repository;

import in.retailflow.api.procurement.domain.PurchaseNumberCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseNumberCounterRepository extends JpaRepository<PurchaseNumberCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PurchaseNumberCounter c WHERE c.tenantId = :tenantId")
    Optional<PurchaseNumberCounter> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
