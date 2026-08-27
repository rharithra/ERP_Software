package in.retailflow.api.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseRequest(
        @NotBlank String supplierId,
        @NotNull LocalDate purchaseDate,
        @Size(max = 1000) String notes,
        @NotEmpty @Valid List<PurchaseItemRequest> items) {

    public record PurchaseItemRequest(
            @NotBlank String productId,
            @NotNull
                    @DecimalMin(value = "0.001", inclusive = true, message = "Quantity must be greater than zero")
                    @Digits(integer = 16, fraction = 3)
                    BigDecimal quantity,
            @NotNull
                    @DecimalMin(value = "0.00", inclusive = true, message = "Unit cost cannot be negative")
                    @Digits(integer = 10, fraction = 2)
                    BigDecimal unitCost,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 2, fraction = 2) BigDecimal gstRate) {}
}
