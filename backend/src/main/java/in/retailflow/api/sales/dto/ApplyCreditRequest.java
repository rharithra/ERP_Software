package in.retailflow.api.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ApplyCreditRequest(@NotNull @Positive BigDecimal amount) {}
