package in.retailflow.api.inventory.dto;

import in.retailflow.api.inventory.domain.InventoryStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record InventoryItemResponse(
        String productId,
        String productName,
        String sku,
        String categoryId,
        String categoryName,
        String unit,
        BigDecimal quantity,
        BigDecimal reorderLevel,
        InventoryStatus status,
        boolean openingRecorded,
        Instant updatedAt) {}
