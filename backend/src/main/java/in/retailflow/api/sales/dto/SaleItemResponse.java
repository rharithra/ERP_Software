package in.retailflow.api.sales.dto;

import java.math.BigDecimal;

public record SaleItemResponse(
        String id,
        String productId,
        String productName,
        String sku,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal gstRate,
        BigDecimal discount,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        BigDecimal lineTotal) {}
