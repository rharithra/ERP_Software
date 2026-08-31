package in.retailflow.api.sales.dto;

import in.retailflow.api.sales.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record OutstandingRowResponse(
        String customerId,
        String customerName,
        String saleId,
        String invoiceNumber,
        LocalDate saleDate,
        BigDecimal grandTotal,
        BigDecimal paid,
        BigDecimal outstanding,
        PaymentStatus paymentStatus) {}
