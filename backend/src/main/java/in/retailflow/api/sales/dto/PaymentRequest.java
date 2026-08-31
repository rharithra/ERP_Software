package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRequest(
        String saleId,
        String salesOrderId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull PaymentMethod paymentMethod,
        @NotNull LocalDate paymentDate,
        @Size(max = 64) String referenceNumber,
        @Size(max = 1000) String notes) {}
