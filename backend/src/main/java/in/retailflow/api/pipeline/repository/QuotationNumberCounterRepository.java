package in.retailflow.api.pipeline.repository;

import in.retailflow.api.pipeline.domain.QuotationNumberCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationNumberCounterRepository extends JpaRepository<QuotationNumberCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM QuotationNumberCounter c WHERE c.tenantId = :tenantId")
    Optional<QuotationNumberCounter> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
