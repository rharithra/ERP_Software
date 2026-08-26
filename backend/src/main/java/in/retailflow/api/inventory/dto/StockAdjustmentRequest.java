package in.retailflow.api.inventory.dto;

import in.retailflow.api.inventory.domain.AdjustmentReason;
import in.retailflow.api.inventory.domain.StockMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StockAdjustmentRequest(
        @NotNull StockMovementType type,
        @NotNull
                @DecimalMin(value = "0.001", inclusive = true, message = "Adjustment quantity must be greater than zero")
                @Digits(integer = 16, fraction = 3)
                BigDecimal quantity,
        @NotNull AdjustmentReason reason,
        @Size(max = 500) String notes) {}
