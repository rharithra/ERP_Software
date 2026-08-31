package in.retailflow.api.pipeline.repository;

import in.retailflow.api.pipeline.domain.AppNotification;
import in.retailflow.api.pipeline.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<AppNotification, UUID> {

    List<AppNotification> findTop50ByOrderByCreatedAtDesc();

    long countByReadAtIsNull();

    @Query(
            """
            SELECT COUNT(n) > 0 FROM AppNotification n
            WHERE n.type = :type AND n.entityId = :entityId AND n.createdAt >= :since
            """)
    boolean existsRecent(
            @Param("type") NotificationType type, @Param("entityId") UUID entityId, @Param("since") Instant since);
}
