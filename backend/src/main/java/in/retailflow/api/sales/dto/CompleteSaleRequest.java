package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CompleteSaleRequest(
        @NotNull PaymentMethod paymentMethod, BigDecimal creditAmount, BigDecimal paymentAmount) {}
