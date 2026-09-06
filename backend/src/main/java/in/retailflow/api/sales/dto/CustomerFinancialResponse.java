package in.retailflow.api.sales.dto;

import java.math.BigDecimal;

public record CustomerFinancialResponse(
        String customerId,
        String customerName,
        BigDecimal totalSales,
        BigDecimal totalPaid,
        BigDecimal outstanding,
        BigDecimal availableCredit) {}
