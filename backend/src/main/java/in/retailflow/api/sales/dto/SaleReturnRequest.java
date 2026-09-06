package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.ReturnReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SaleReturnRequest(
        @NotNull UUID saleId,
        LocalDate returnDate,
        ReturnReason reason,
        String notes,
        @NotEmpty @Valid List<Item> items) {

    public record Item(@NotNull UUID saleItemId, @NotNull BigDecimal quantity, ReturnReason reason) {}
}
