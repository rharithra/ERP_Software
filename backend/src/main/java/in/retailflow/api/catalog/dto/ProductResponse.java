package in.retailflow.api.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        String id,
        String name,
        String sku,
        String barcode,
        String description,
        String categoryId,
        String categoryName,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        BigDecimal gstRate,
        String unit,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
