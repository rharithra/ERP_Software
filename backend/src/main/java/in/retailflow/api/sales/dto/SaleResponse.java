package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.PaymentMethod;
import in.retailflow.api.sales.domain.PaymentStatus;
import in.retailflow.api.sales.domain.SaleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SaleResponse(
        String id,
        String saleNumber,
        String invoiceNumber,
        String customerId,
        String customerName,
        String customerPhone,
        String customerGstin,
        LocalDate saleDate,
        SaleStatus status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        String notes,
        Instant completedAt,
        String createdBy,
        String createdByName,
        Instant createdAt,
        Instant updatedAt,
        int itemCount,
        List<SaleItemResponse> items,
        String salesOrderId,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount) {}
