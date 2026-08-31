package in.retailflow.api.pipeline.dto;

import in.retailflow.api.pipeline.domain.PipelineActivityType;
import java.time.Instant;

public record PipelineActivityResponse(
        String id, PipelineActivityType activityType, String message, String referenceType, String referenceId, Instant createdAt) {}
