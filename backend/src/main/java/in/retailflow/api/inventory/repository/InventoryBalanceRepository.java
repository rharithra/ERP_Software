package in.retailflow.api.inventory.repository;

import in.retailflow.api.inventory.domain.InventoryBalance;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBalance b WHERE b.product.id = :productId")
    Optional<InventoryBalance> findByProductIdForUpdate(@Param("productId") UUID productId);

    Optional<InventoryBalance> findByProduct_Id(UUID productId);

    List<InventoryBalance> findByProduct_IdIn(Collection<UUID> productIds);

    boolean existsByProduct_Id(UUID productId);
}
