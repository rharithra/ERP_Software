package in.retailflow.api.sales.dto;

import java.math.BigDecimal;
import java.util.List;

public record SaleDashboardResponse(
        long todayOrders, BigDecimal todayRevenue, List<SaleResponse> recentSales) {}
