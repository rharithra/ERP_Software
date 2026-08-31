package in.retailflow.api.pipeline.repository;

import in.retailflow.api.pipeline.domain.FollowUp;
import in.retailflow.api.pipeline.domain.FollowUpStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowUpRepository extends JpaRepository<FollowUp, UUID> {

    List<FollowUp> findByLeadIdOrderByDueDateAscDueTimeAsc(UUID leadId);

    @Query(
            """
            SELECT f FROM FollowUp f
            WHERE (:status IS NULL OR f.status = :status)
              AND (:fromDate IS NULL OR f.dueDate >= :fromDate)
              AND (:toDate IS NULL OR f.dueDate <= :toDate)
            ORDER BY f.dueDate ASC, f.dueTime ASC NULLS LAST
            """)
    List<FollowUp> search(
            @Param("status") FollowUpStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT COUNT(f) FROM FollowUp f WHERE CAST(f.status AS string) = 'PENDING' AND f.dueDate = :today")
    long countDueToday(@Param("today") LocalDate today);

    @Query("SELECT COUNT(f) FROM FollowUp f WHERE CAST(f.status AS string) = 'PENDING' AND f.dueDate < :today")
    long countOverdue(@Param("today") LocalDate today);

    List<FollowUp> findByStatusAndDueDate(FollowUpStatus status, LocalDate dueDate);

    List<FollowUp> findByStatusAndDueDateLessThan(FollowUpStatus status, LocalDate dueDate);
}
