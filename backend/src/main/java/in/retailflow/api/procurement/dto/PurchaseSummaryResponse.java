package in.retailflow.api.procurement.dto;

import java.math.BigDecimal;

public record PurchaseSummaryResponse(
        long totalPurchases, long draftPurchases, long receivedPurchases, BigDecimal receivedValue) {}
