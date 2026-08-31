package in.retailflow.api.pipeline.repository;

import in.retailflow.api.pipeline.domain.LeadNumberCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadNumberCounterRepository extends JpaRepository<LeadNumberCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM LeadNumberCounter c WHERE c.tenantId = :tenantId")
    Optional<LeadNumberCounter> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
