package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.ReturnReason;
import in.retailflow.api.sales.domain.SaleReturnStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SaleReturnResponse(
        String id,
        String returnNumber,
        String saleId,
        String saleNumber,
        String invoiceNumber,
        String customerId,
        String customerName,
        SaleReturnStatus status,
        LocalDate returnDate,
        ReturnReason reason,
        String notes,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String createdBy,
        String createdByName,
        Instant createdAt,
        Instant completedAt,
        List<SaleReturnItemResponse> items) {}
