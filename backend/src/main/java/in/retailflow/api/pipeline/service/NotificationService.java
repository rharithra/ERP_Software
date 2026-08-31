package in.retailflow.api.pipeline.service;

import in.retailflow.api.pipeline.domain.AppNotification;
import in.retailflow.api.pipeline.domain.FollowUp;
import in.retailflow.api.pipeline.domain.FollowUpStatus;
import in.retailflow.api.pipeline.domain.NotificationType;
import in.retailflow.api.pipeline.domain.Quotation;
import in.retailflow.api.pipeline.dto.NotificationResponse;
import in.retailflow.api.pipeline.repository.FollowUpRepository;
import in.retailflow.api.pipeline.repository.NotificationRepository;
import in.retailflow.api.pipeline.repository.QuotationRepository;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FollowUpRepository followUpRepository;
    private final QuotationRepository quotationRepository;
    private final PipelineSupport support;

    public NotificationService(
            NotificationRepository notificationRepository,
            FollowUpRepository followUpRepository,
            QuotationRepository quotationRepository,
            PipelineSupport support) {
        this.notificationRepository = notificationRepository;
        this.followUpRepository = followUpRepository;
        this.quotationRepository = quotationRepository;
        this.support = support;
    }

    @Transactional
    public List<NotificationResponse> listAndRefresh() {
        refreshOperational();
        return notificationRepository.findTop50ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByReadAtIsNull();
    }

    @Transactional
    public void markRead(UUID id) {
        notificationRepository.findById(id).ifPresent(AppNotification::markRead);
    }

    @Transactional
    public void markAllRead() {
        notificationRepository.findTop50ByOrderByCreatedAtDesc().forEach(AppNotification::markRead);
    }

    public void followUpCreated(FollowUp followUp) {
        notify(
                followUp.getAssignedTo() == null ? TenantContext.require().userId() : followUp.getAssignedTo(),
                NotificationType.FOLLOW_UP_DUE,
                "Follow-up scheduled",
                followUp.getLead().getName() + " — " + followUp.getType(),
                "FOLLOW_UP",
                followUp.getId());
    }

    public void quotationAccepted(Quotation quotation) {
        notify(
                TenantContext.require().userId(),
                NotificationType.QUOTATION_ACCEPTED,
                "Quotation accepted",
                quotation.getQuotationNumber() + " for " + quotation.getCustomer().getName(),
                "QUOTATION",
                quotation.getId());
    }

    public void paymentOutstanding(UUID userId, String title, String body, UUID saleId) {
        notify(userId, NotificationType.PAYMENT_OUTSTANDING, title, body, "SALE", saleId);
    }

    private void refreshOperational() {
        Tenant tenant = support.currentTenant();
        LocalDate today = LocalDate.now(ZoneId.of(tenant.getTimezone() == null ? "Asia/Kolkata" : tenant.getTimezone()));
        Instant since = Instant.now().minus(20, ChronoUnit.HOURS);
        for (FollowUp followUp : followUpRepository.findByStatusAndDueDate(FollowUpStatus.PENDING, today)) {
            if (!notificationRepository.existsRecent(NotificationType.FOLLOW_UP_DUE, followUp.getId(), since)) {
                notify(
                        followUp.getAssignedTo() == null ? TenantContext.require().userId() : followUp.getAssignedTo(),
                        NotificationType.FOLLOW_UP_DUE,
                        "Follow-up due today",
                        followUp.getLead().getName(),
                        "FOLLOW_UP",
                        followUp.getId());
            }
        }
        for (FollowUp followUp : followUpRepository.findByStatusAndDueDateLessThan(FollowUpStatus.PENDING, today)) {
            if (!notificationRepository.existsRecent(NotificationType.FOLLOW_UP_OVERDUE, followUp.getId(), since)) {
                notify(
                        followUp.getAssignedTo() == null ? TenantContext.require().userId() : followUp.getAssignedTo(),
                        NotificationType.FOLLOW_UP_OVERDUE,
                        "Follow-up overdue",
                        followUp.getLead().getName(),
                        "FOLLOW_UP",
                        followUp.getId());
            }
        }
        LocalDate soon = today.plusDays(2);
        for (Quotation quotation :
                quotationRepository.findByStatusAndValidUntilLessThanEqual(
                        in.retailflow.api.pipeline.domain.QuotationStatus.SENT, soon)) {
            if (!quotation.getValidUntil().isBefore(today)
                    && !notificationRepository.existsRecent(NotificationType.QUOTATION_EXPIRING, quotation.getId(), since)) {
                notify(
                        TenantContext.require().userId(),
                        NotificationType.QUOTATION_EXPIRING,
                        "Quotation expires soon",
                        quotation.getQuotationNumber() + " valid until " + quotation.getValidUntil(),
                        "QUOTATION",
                        quotation.getId());
            }
        }
    }

    private void notify(
            UUID userId, NotificationType type, String title, String body, String entityType, UUID entityId) {
        notificationRepository.save(new AppNotification(
                UUID.randomUUID(), support.currentTenant(), userId, type, title, body, entityType, entityId));
    }

    private NotificationResponse toResponse(AppNotification notification) {
        return new NotificationResponse(
                notification.getId().toString(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getEntityType(),
                notification.getEntityId() == null ? null : notification.getEntityId().toString(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
