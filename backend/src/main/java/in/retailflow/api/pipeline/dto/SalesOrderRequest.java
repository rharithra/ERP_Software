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

public record SalesOrderRequest(
        String quotationId,
        String leadId,
        @jakarta.validation.constraints.NotBlank String customerId,
        @NotNull LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal discount,
        @Size(max = 2000) String notes,
        @NotEmpty @Valid List<QuotationRequest.LineRequest> items) {}
