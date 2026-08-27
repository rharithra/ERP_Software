package in.retailflow.api.procurement.dto;

import in.retailflow.api.procurement.domain.PurchaseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PurchaseResponse(
        String id,
        String purchaseNumber,
        String supplierId,
        String supplierName,
        LocalDate purchaseDate,
        PurchaseStatus status,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String notes,
        Instant receivedAt,
        String createdBy,
        String createdByName,
        Instant createdAt,
        Instant updatedAt,
        int itemCount,
        List<PurchaseItemResponse> items) {}
