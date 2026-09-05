package in.retailflow.api.identity.repository;

import in.retailflow.api.identity.domain.UserManagementEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserManagementEventRepository extends JpaRepository<UserManagementEvent, UUID> {

    List<UserManagementEvent> findByTenant_IdOrderByCreatedAtDesc(UUID tenantId);
}
