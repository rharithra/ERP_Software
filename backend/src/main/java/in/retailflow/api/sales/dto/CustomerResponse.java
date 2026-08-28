package in.retailflow.api.sales.dto;

import java.time.Instant;

public record CustomerResponse(
        String id,
        String name,
        String phone,
        String email,
        String address,
        String gstin,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
