package in.retailflow.api.pipeline.dto;

import in.retailflow.api.pipeline.domain.NotificationType;
import java.time.Instant;

public record NotificationResponse(
        String id, NotificationType type, String title, String body, String entityType, String entityId, Instant readAt, Instant createdAt) {}
