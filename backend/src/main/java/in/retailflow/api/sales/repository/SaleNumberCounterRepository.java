package in.retailflow.api.sales.repository;

import in.retailflow.api.sales.domain.SaleNumberCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleNumberCounterRepository extends JpaRepository<SaleNumberCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SaleNumberCounter c WHERE c.tenantId = :tenantId")
    Optional<SaleNumberCounter> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
