package in.retailflow.api.pipeline.dto;

import in.retailflow.api.pipeline.domain.SalesOrderStatus;
import in.retailflow.api.sales.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderResponse(
        String id,
        String orderNumber,
        String quotationId,
        String quotationNumber,
        String leadId,
        String customerId,
        String customerName,
        String saleId,
        String saleNumber,
        String invoiceNumber,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        SalesOrderStatus status,
        PaymentStatus paymentStatus,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        BigDecimal advancePaid,
        BigDecimal outstandingAmount,
        String notes,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        int itemCount,
        List<LineItemResponse> items) {}
