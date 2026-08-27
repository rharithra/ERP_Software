package in.retailflow.api.procurement.dto;

import java.time.Instant;

public record SupplierResponse(
        String id,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String gstin,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
