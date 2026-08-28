package in.retailflow.api.sales.dto;

import java.math.BigDecimal;

public record SaleSummaryResponse(
        long totalSales, long draftSales, long completedSales, BigDecimal completedValue) {}
