package in.retailflow.api.pipeline.dto;

import java.math.BigDecimal;

public record LineItemResponse(
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
