package in.retailflow.api.tenant.repository;

import in.retailflow.api.tenant.domain.TenantMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {

    @Query("SELECT m FROM TenantMembership m JOIN FETCH m.tenant JOIN FETCH m.user WHERE m.user.id = :userId")
    List<TenantMembership> findByUserIdWithTenant(@Param("userId") UUID userId);

    @Query("SELECT m FROM TenantMembership m JOIN FETCH m.tenant JOIN FETCH m.user WHERE m.user.id = :userId AND m.tenant.id = :tenantId")
    Optional<TenantMembership> findByUserIdAndTenantId(
            @Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}
