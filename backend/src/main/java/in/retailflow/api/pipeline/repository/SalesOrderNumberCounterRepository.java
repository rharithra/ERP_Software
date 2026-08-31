package in.retailflow.api.pipeline.repository;

import in.retailflow.api.pipeline.domain.SalesOrderNumberCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderNumberCounterRepository extends JpaRepository<SalesOrderNumberCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SalesOrderNumberCounter c WHERE c.tenantId = :tenantId")
    Optional<SalesOrderNumberCounter> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
