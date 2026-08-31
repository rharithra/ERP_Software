package in.retailflow.api.pipeline.repository;

import in.retailflow.api.pipeline.domain.SalesOrder;
import in.retailflow.api.pipeline.domain.SalesOrderStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {

    @EntityGraph(attributePaths = {"customer", "lead", "quotation", "items", "items.product"})
    Optional<SalesOrder> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SalesOrder o WHERE o.id = :id")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            """
            SELECT o FROM SalesOrder o
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(o.customer.name) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR o.status = :status)
              AND (:customerId IS NULL OR o.customer.id = :customerId)
            """)
    Page<SalesOrder> search(
            @Param("q") String q,
            @Param("status") SalesOrderStatus status,
            @Param("customerId") UUID customerId,
            Pageable pageable);

    List<SalesOrder> findByLeadIdOrderByCreatedAtDesc(UUID leadId);

    @Query(
            """
            SELECT COUNT(o), COALESCE(SUM(o.grandTotal), 0)
            FROM SalesOrder o
            WHERE CAST(o.status AS string) NOT IN ('COMPLETED', 'CANCELLED')
            """)
    Object[] openSummary();
}
