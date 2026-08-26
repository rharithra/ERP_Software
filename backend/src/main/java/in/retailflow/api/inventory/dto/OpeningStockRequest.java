package in.retailflow.api.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record OpeningStockRequest(
        @NotNull
                @DecimalMin(value = "0.001", inclusive = true, message = "Opening quantity must be greater than zero")
                @Digits(integer = 16, fraction = 3)
                BigDecimal quantity,
        @Size(max = 500) String notes) {}
