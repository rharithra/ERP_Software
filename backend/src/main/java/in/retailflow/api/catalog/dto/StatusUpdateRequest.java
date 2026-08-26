package in.retailflow.api.catalog.dto;

import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull Boolean active) {}
