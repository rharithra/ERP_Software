package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.CreditTransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record CreditTransactionResponse(
        String id,
        CreditTransactionType type,
        BigDecimal amount,
        String saleId,
        String saleReturnId,
        String refundId,
        String reference,
        String createdBy,
        String createdByName,
        Instant createdAt) {}
