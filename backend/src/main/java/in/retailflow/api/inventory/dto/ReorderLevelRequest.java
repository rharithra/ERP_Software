package in.retailflow.api.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ReorderLevelRequest(
        @NotNull
                @DecimalMin(value = "0.000", inclusive = true, message = "Reorder level cannot be negative")
                @Digits(integer = 16, fraction = 3)
                BigDecimal reorderLevel) {}
