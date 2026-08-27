package in.retailflow.api.procurement.dto;

import java.math.BigDecimal;

public record PurchaseItemResponse(
        String id,
        String productId,
        String productName,
        String sku,
        String unit,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal gstRate,
        BigDecimal lineSubtotal,
        BigDecimal taxAmount,
        BigDecimal lineTotal) {}
