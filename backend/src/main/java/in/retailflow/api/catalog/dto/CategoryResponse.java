package in.retailflow.api.catalog.dto;

import java.time.Instant;

public record CategoryResponse(
        String id, String name, String description, boolean active, Instant createdAt, Instant updatedAt) {}
