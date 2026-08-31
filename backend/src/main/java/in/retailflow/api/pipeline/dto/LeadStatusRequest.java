package in.retailflow.api.pipeline.dto;

import in.retailflow.api.pipeline.domain.LeadStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LeadStatusRequest(@NotNull LeadStatus status, @Size(max = 1000) String lostReason) {}
