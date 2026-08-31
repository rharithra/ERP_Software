package in.retailflow.api.pipeline.dto;

import jakarta.validation.constraints.Size;

public record FollowUpOutcomeRequest(@Size(max = 2000) String outcome) {}
