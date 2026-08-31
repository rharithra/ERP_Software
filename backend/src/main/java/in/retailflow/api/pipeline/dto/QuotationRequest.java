package in.retailflow.api.pipeline.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record QuotationRequest(
        String leadId,
        @jakarta.validation.constraints.NotBlank String customerId,
        @NotNull LocalDate quotationDate,
        @NotNull LocalDate validUntil,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal discount,
        @Size(max = 2000) String notes,
        @Size(max = 4000) String termsAndConditions,
        @NotEmpty @Valid List<LineRequest> items) {

    public record LineRequest(
            @jakarta.validation.constraints.NotBlank String productId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 16, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @NotNull @DecimalMin("0.00") @Digits(integer = 2, fraction = 2) BigDecimal gstRate,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal discount) {}
}
