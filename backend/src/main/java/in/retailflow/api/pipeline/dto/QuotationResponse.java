package in.retailflow.api.pipeline.dto;

import in.retailflow.api.pipeline.domain.QuotationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record QuotationResponse(
        String id,
        String quotationNumber,
        String leadId,
        String leadName,
        String customerId,
        String customerName,
        LocalDate quotationDate,
        LocalDate validUntil,
        QuotationStatus status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String notes,
        String termsAndConditions,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        int itemCount,
        List<LineItemResponse> items) {}
