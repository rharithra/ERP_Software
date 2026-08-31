package in.retailflow.api.pipeline.service;

import in.retailflow.api.pipeline.domain.LeadStatus;
import in.retailflow.api.pipeline.dto.PipelineDashboardResponse;
import in.retailflow.api.pipeline.repository.FollowUpRepository;
import in.retailflow.api.pipeline.repository.LeadRepository;
import in.retailflow.api.pipeline.repository.QuotationRepository;
import in.retailflow.api.pipeline.repository.SalesOrderRepository;
import in.retailflow.api.sales.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PipelineDashboardService {

    private final LeadRepository leadRepository;
    private final FollowUpRepository followUpRepository;
    private final QuotationRepository quotationRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PaymentService paymentService;
    private final PipelineSupport support;

    public PipelineDashboardService(
            LeadRepository leadRepository,
            FollowUpRepository followUpRepository,
            QuotationRepository quotationRepository,
            SalesOrderRepository salesOrderRepository,
            PaymentService paymentService,
            PipelineSupport support) {
        this.leadRepository = leadRepository;
        this.followUpRepository = followUpRepository;
        this.quotationRepository = quotationRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.paymentService = paymentService;
        this.support = support;
    }

    @Transactional(readOnly = true)
    public PipelineDashboardResponse dashboard() {
        ZoneId zone = ZoneId.of(
                support.currentTenant().getTimezone() == null ? "Asia/Kolkata" : support.currentTenant().getTimezone());
        LocalDate today = LocalDate.now(zone);
        Instant monthStart = ZonedDateTime.now(zone).withDayOfMonth(1).toLocalDate().atStartOfDay(zone).toInstant();
        Object[] quotes = unwrap(quotationRepository.openSummary());
        Object[] orders = unwrap(salesOrderRepository.openSummary());
        return new PipelineDashboardResponse(
                leadRepository.countByStatus(LeadStatus.NEW),
                leadRepository.countOpen(),
                asLong(quotes[0]),
                asMoney(quotes[1]),
                asLong(orders[0]),
                asMoney(orders[1]),
                leadRepository.countStatusSince(LeadStatus.WON, monthStart),
                leadRepository.countStatusSince(LeadStatus.LOST, monthStart),
                followUpRepository.countDueToday(today),
                followUpRepository.countOverdue(today),
                paymentService.outstanding("OUTSTANDING").stream()
                        .map(in.retailflow.api.sales.dto.OutstandingRowResponse::outstanding)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static Object[] unwrap(Object[] row) {
        if (row != null && row.length >= 2 && !(row[0] instanceof Object[])) {
            return row;
        }
        return row == null || row.length == 0 ? new Object[] {0, BigDecimal.ZERO} : (Object[]) row[0];
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static BigDecimal asMoney(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }
}
