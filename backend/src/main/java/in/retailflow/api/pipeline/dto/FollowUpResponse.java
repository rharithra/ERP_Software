package in.retailflow.api.pipeline.dto;

import in.retailflow.api.pipeline.domain.FollowUpStatus;
import in.retailflow.api.pipeline.domain.FollowUpType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record FollowUpResponse(
        String id,
        String leadId,
        String leadName,
        String leadNumber,
        String customerId,
        FollowUpType type,
        LocalDate dueDate,
        LocalTime dueTime,
        String assignedTo,
        String assignedToName,
        FollowUpStatus status,
        String notes,
        String outcome,
        Instant completedAt,
        boolean overdue,
        Instant createdAt,
        Instant updatedAt) {}
