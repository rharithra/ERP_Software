package in.retailflow.api.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(min = 2, max = 200) String name,
        @NotBlank String categoryId,
        @Size(max = 1000) String description,
        @NotBlank @Size(min = 2, max = 64) String sku,
        @Size(max = 64) String barcode,
        @NotNull @DecimalMin(value = "0.00", inclusive = true, message = "Cost price cannot be negative")
                @Digits(integer = 10, fraction = 2)
                BigDecimal costPrice,
        @NotNull @DecimalMin(value = "0.00", inclusive = true, message = "Selling price cannot be negative")
                @Digits(integer = 10, fraction = 2)
                BigDecimal sellingPrice,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 2, fraction = 2) BigDecimal gstRate,
        @NotBlank String unit) {}
