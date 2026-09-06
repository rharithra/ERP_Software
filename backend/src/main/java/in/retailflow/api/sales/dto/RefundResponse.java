package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.PaymentMethod;
import in.retailflow.api.sales.domain.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record RefundResponse(
        String id,
        String refundNumber,
        String saleId,
        String saleNumber,
        String invoiceNumber,
        String saleReturnId,
        String customerId,
        String customerName,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        RefundStatus status,
        String referenceNumber,
        String notes,
        String createdBy,
        String createdByName,
        Instant createdAt,
        Instant completedAt) {}
