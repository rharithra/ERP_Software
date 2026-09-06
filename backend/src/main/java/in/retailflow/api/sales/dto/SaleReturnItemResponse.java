package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.ReturnReason;
import java.math.BigDecimal;

public record SaleReturnItemResponse(
        String id,
        String saleItemId,
        String productId,
        String productName,
        String sku,
        String barcode,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal gstRate,
        BigDecimal discount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        ReturnReason reason) {}
