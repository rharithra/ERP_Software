package in.retailflow.api.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SaleRequest(
        String customerId,
        @NotNull LocalDate saleDate,
        @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal discount,
        @Size(max = 1000) String notes,
        @NotEmpty @Valid List<SaleItemRequest> items) {

    public record SaleItemRequest(
            @jakarta.validation.constraints.NotBlank String productId,
            @NotNull
                    @DecimalMin(value = "0.001", inclusive = true, message = "Quantity must be greater than zero")
                    @Digits(integer = 16, fraction = 3)
                    BigDecimal quantity,
            @NotNull
                    @DecimalMin(value = "0.00", inclusive = true)
                    @Digits(integer = 10, fraction = 2)
                    BigDecimal unitPrice,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 2, fraction = 2) BigDecimal gstRate,
            @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal discount) {}
}
