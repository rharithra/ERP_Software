package in.retailflow.api.pipeline.repository;

import in.retailflow.api.pipeline.domain.Quotation;
import in.retailflow.api.pipeline.domain.QuotationStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
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

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {

    @EntityGraph(attributePaths = {"customer", "lead", "items", "items.product"})
    Optional<Quotation> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Quotation q WHERE q.id = :id")
    Optional<Quotation> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            """
            SELECT q FROM Quotation q
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(q.quotationNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(q.customer.name) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR q.status = :status)
              AND (:customerId IS NULL OR q.customer.id = :customerId)
            """)
    Page<Quotation> search(
            @Param("q") String q,
            @Param("status") QuotationStatus status,
            @Param("customerId") UUID customerId,
            Pageable pageable);

    List<Quotation> findByLeadIdOrderByCreatedAtDesc(UUID leadId);

    @Query(
            """
            SELECT COUNT(q), COALESCE(SUM(q.grandTotal), 0)
            FROM Quotation q
            WHERE CAST(q.status AS string) IN ('DRAFT', 'SENT')
            """)
    Object[] openSummary();

    List<Quotation> findByStatusAndValidUntilLessThanEqual(QuotationStatus status, LocalDate date);
}
