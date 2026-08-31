package in.retailflow.api.pipeline.repository;

import in.retailflow.api.pipeline.domain.Lead;
import in.retailflow.api.pipeline.domain.LeadStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    @Query(
            """
            SELECT l FROM Lead l
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(l.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(l.leadNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(l.phone, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(l.requirement, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR l.status = :status)
              AND (:priority IS NULL OR CAST(l.priority AS string) = :priority)
            """)
    Page<Lead> search(
            @Param("q") String q,
            @Param("status") LeadStatus status,
            @Param("priority") String priority,
            Pageable pageable);

    List<Lead> findAllByOrderByUpdatedAtDesc();

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.status = :status")
    long countByStatus(@Param("status") LeadStatus status);

    @Query("SELECT COUNT(l) FROM Lead l WHERE CAST(l.status AS string) NOT IN ('WON', 'LOST')")
    long countOpen();

    @Query("SELECT COALESCE(SUM(l.expectedValue), 0) FROM Lead l WHERE CAST(l.status AS string) NOT IN ('WON', 'LOST')")
    java.math.BigDecimal openValue();

    @Query(
            """
            SELECT COUNT(l) FROM Lead l
            WHERE l.status = :status AND l.updatedAt >= :from
            """)
    long countStatusSince(@Param("status") LeadStatus status, @Param("from") Instant from);
}
