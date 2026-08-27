package in.retailflow.api.procurement.repository;

import in.retailflow.api.procurement.domain.Purchase;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    @EntityGraph(attributePaths = "supplier")
    @Query(
            """
            SELECT p FROM Purchase p
            WHERE (:q IS NULL OR :q = '' OR LOWER(p.purchaseNumber) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:supplierId IS NULL OR p.supplier.id = :supplierId)
              AND (:status IS NULL OR :status = '' OR CAST(p.status AS string) = :status)
              AND (:fromDate IS NULL OR p.purchaseDate >= :fromDate)
              AND (:toDate IS NULL OR p.purchaseDate <= :toDate)
            """)
    Page<Purchase> search(
            @Param("q") String q,
            @Param("supplierId") UUID supplierId,
            @Param("status") String status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "items", "items.product"})
    Optional<Purchase> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Purchase p WHERE p.id = :id")
    Optional<Purchase> findByIdForUpdate(@Param("id") UUID id);

    boolean existsBySupplier_Id(UUID supplierId);

    @Query(
            """
            SELECT COUNT(p),
                   COALESCE(SUM(CASE WHEN CAST(p.status AS string) = 'DRAFT' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN CAST(p.status AS string) = 'RECEIVED' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN CAST(p.status AS string) = 'RECEIVED' THEN p.totalAmount ELSE 0 END), 0)
            FROM Purchase p
            """)
    Object[] summarize();
}
