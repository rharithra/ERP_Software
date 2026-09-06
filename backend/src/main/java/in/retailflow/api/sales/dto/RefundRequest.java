package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequest(
        @NotNull UUID saleId,
        UUID saleReturnId,
        @NotNull @Positive BigDecimal amount,
        @NotNull PaymentMethod paymentMethod,
        String referenceNumber,
        String notes) {}
