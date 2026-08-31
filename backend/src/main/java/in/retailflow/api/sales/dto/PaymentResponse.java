package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaymentResponse(
        String id,
        String paymentNumber,
        String saleId,
        String invoiceNumber,
        String salesOrderId,
        String orderNumber,
        String customerId,
        String customerName,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        LocalDate paymentDate,
        String referenceNumber,
        String notes,
        String createdBy,
        String createdByName,
        Instant createdAt) {}
