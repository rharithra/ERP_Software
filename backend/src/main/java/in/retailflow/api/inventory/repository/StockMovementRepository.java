package in.retailflow.api.inventory.repository;

import in.retailflow.api.inventory.domain.StockMovement;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByProduct_IdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    boolean existsByProduct_Id(UUID productId);
}
