package in.retailflow.api.sales.repository;

import in.retailflow.api.sales.domain.Sale;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
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

public interface SaleRepository extends JpaRepository<Sale, UUID> {

    @EntityGraph(attributePaths = "customer")
    @Query(
            """
            SELECT s FROM Sale s
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(s.saleNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(s.invoiceNumber, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(s.customerName) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:customerId IS NULL OR s.customer.id = :customerId)
              AND (:status IS NULL OR :status = '' OR CAST(s.status AS string) = :status)
              AND (:fromDate IS NULL OR s.saleDate >= :fromDate)
              AND (:toDate IS NULL OR s.saleDate <= :toDate)
            """)
    Page<Sale> search(
            @Param("q") String q,
            @Param("customerId") UUID customerId,
            @Param("status") String status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "items", "items.product"})
    Optional<Sale> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sale s WHERE s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            """
            SELECT COUNT(s),
                   COALESCE(SUM(CASE WHEN CAST(s.status AS string) = 'DRAFT' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN CAST(s.status AS string) = 'COMPLETED' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN CAST(s.status AS string) = 'COMPLETED' THEN s.grandTotal ELSE 0 END), 0)
            FROM Sale s
            """)
    Object[] summarize();

    @Query(
            """
            SELECT COUNT(s),
                   COALESCE(SUM(s.grandTotal), 0)
            FROM Sale s
            WHERE CAST(s.status AS string) = 'COMPLETED'
              AND s.saleDate = :saleDate
            """)
    Object[] summarizeToday(@Param("saleDate") LocalDate saleDate);

    @EntityGraph(attributePaths = "customer")
    @Query(
            """
            SELECT s FROM Sale s
            WHERE CAST(s.status AS string) = 'COMPLETED'
            ORDER BY s.completedAt DESC
            """)
    List<Sale> findRecentCompleted(Pageable pageable);
}
