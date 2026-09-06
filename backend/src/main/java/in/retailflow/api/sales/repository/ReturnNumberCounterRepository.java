package in.retailflow.api.sales.repository;

import in.retailflow.api.sales.domain.ReturnNumberCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReturnNumberCounterRepository extends JpaRepository<ReturnNumberCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ReturnNumberCounter c WHERE c.tenantId = :tenantId")
    Optional<ReturnNumberCounter> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
