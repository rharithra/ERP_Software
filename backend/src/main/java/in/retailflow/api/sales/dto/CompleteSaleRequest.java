package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CompleteSaleRequest(@NotNull PaymentMethod paymentMethod) {}
