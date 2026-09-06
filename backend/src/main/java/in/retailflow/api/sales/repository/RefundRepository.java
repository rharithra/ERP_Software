package in.retailflow.api.sales.repository;

import in.retailflow.api.sales.domain.Refund;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    @EntityGraph(attributePaths = {"sale", "customer", "saleReturn"})
    Optional<Refund> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"sale", "customer"})
    List<Refund> findBySale_IdOrderByCreatedAtDesc(UUID saleId);

    @EntityGraph(attributePaths = {"sale", "customer"})
    List<Refund> findByCustomer_IdOrderByCreatedAtDesc(UUID customerId);

    @Query(
            """
            SELECT COALESCE(SUM(r.amount), 0)
            FROM Refund r
            WHERE r.sale.id = :saleId
              AND CAST(r.status AS string) = 'COMPLETED'
            """)
    BigDecimal sumCompletedForSale(@Param("saleId") UUID saleId);

    @EntityGraph(attributePaths = {"sale", "customer"})
    @Query(
            """
            SELECT r FROM Refund r
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(r.refundNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(r.sale.saleNumber) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR :status = '' OR CAST(r.status AS string) = :status)
            ORDER BY r.createdAt DESC
            """)
    List<Refund> search(@Param("q") String q, @Param("status") String status);
}
