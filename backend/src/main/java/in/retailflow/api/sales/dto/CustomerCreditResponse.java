package in.retailflow.api.sales.dto;

import java.math.BigDecimal;
import java.util.List;

public record CustomerCreditResponse(
        String customerId, String customerName, BigDecimal availableCredit, List<CreditTransactionResponse> transactions) {}
