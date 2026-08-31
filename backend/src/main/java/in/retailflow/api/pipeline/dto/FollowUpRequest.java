package in.retailflow.api.pipeline.dto;

import in.retailflow.api.pipeline.domain.FollowUpStatus;
import in.retailflow.api.pipeline.domain.FollowUpType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record FollowUpRequest(
        @NotBlank String leadId,
        String customerId,
        @NotNull FollowUpType type,
        @NotNull LocalDate dueDate,
        LocalTime dueTime,
        String assignedTo,
        @Size(max = 2000) String notes) {}
