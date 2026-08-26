package in.retailflow.api.inventory.dto;

import in.retailflow.api.inventory.domain.AdjustmentReason;
import in.retailflow.api.inventory.domain.StockMovementType;
import java.math.BigDecimal;
import java.time.Instant;

public record StockMovementResponse(
        String id,
        String productId,
        StockMovementType type,
        BigDecimal quantity,
        BigDecimal quantityBefore,
        BigDecimal quantityAfter,
        String referenceType,
        String referenceId,
        AdjustmentReason reason,
        String notes,
        String createdBy,
        String createdByName,
        Instant createdAt) {}
