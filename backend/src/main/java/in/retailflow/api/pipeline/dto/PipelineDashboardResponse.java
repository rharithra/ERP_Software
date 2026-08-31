package in.retailflow.api.pipeline.dto;

import java.math.BigDecimal;

public record PipelineDashboardResponse(
        long newLeads,
        long openLeads,
        long openQuotations,
        BigDecimal quotationValue,
        long openSalesOrders,
        BigDecimal pipelineValue,
        long wonThisMonth,
        long lostThisMonth,
        long followUpsToday,
        long followUpsOverdue,
        BigDecimal outstandingPayments) {}
