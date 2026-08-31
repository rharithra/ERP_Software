package in.retailflow.api.pipeline.service;

import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.pipeline.domain.FollowUp;
import in.retailflow.api.pipeline.domain.FollowUpStatus;
import in.retailflow.api.pipeline.domain.FollowUpType;
import in.retailflow.api.pipeline.domain.Lead;
import in.retailflow.api.pipeline.domain.LeadStatus;
import in.retailflow.api.pipeline.domain.PipelineActivityType;
import in.retailflow.api.pipeline.dto.FollowUpOutcomeRequest;
import in.retailflow.api.pipeline.dto.FollowUpRequest;
import in.retailflow.api.pipeline.dto.FollowUpResponse;
import in.retailflow.api.pipeline.repository.FollowUpRepository;
import in.retailflow.api.sales.domain.Customer;
import in.retailflow.api.sales.service.CustomerService;
import in.retailflow.api.tenant.domain.Tenant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowUpService {

    private final FollowUpRepository followUpRepository;
    private final LeadService leadService;
    private final CustomerService customerService;
    private final PipelineSupport support;
    private final NotificationService notificationService;

    public FollowUpService(
            FollowUpRepository followUpRepository,
            LeadService leadService,
            CustomerService customerService,
            PipelineSupport support,
            NotificationService notificationService) {
        this.followUpRepository = followUpRepository;
        this.leadService = leadService;
        this.customerService = customerService;
        this.support = support;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<FollowUpResponse> list(FollowUpStatus status, LocalDate from, LocalDate to) {
        LocalDate today = today();
        return followUpRepository.search(status, from, to).stream().map(f -> toResponse(f, today)).toList();
    }

    @Transactional(readOnly = true)
    public List<FollowUpResponse> forLead(UUID leadId) {
        LocalDate today = today();
        leadService.require(leadId);
        return followUpRepository.findByLeadIdOrderByDueDateAscDueTimeAsc(leadId).stream()
                .map(f -> toResponse(f, today))
                .toList();
    }

    @Transactional
    public FollowUpResponse create(FollowUpRequest request) {
        Tenant tenant = support.currentTenant();
        Lead lead = leadService.require(PipelineSupport.parseUuid(request.leadId(), "leadId"));
        FollowUp followUp = new FollowUp(UUID.randomUUID(), tenant, lead, request.type(), request.dueDate());
        apply(followUp, request, lead);
        followUpRepository.save(followUp);
        if (lead.getStatus() == LeadStatus.NEW) {
            lead.changeStatus(LeadStatus.CONTACTED, null);
        }
        leadService.record(
                lead, PipelineActivityType.FOLLOW_UP_CREATED, "Follow-up scheduled (" + request.type() + ")", "FOLLOW_UP", followUp.getId());
        notificationService.followUpCreated(followUp);
        return toResponse(followUp, today());
    }

    @Transactional
    public FollowUpResponse update(UUID id, FollowUpRequest request) {
        FollowUp followUp = require(id);
        followUp.requirePending();
        apply(followUp, request, followUp.getLead());
        return toResponse(followUp, today());
    }

    @Transactional
    public FollowUpResponse complete(UUID id, FollowUpOutcomeRequest request) {
        FollowUp followUp = require(id);
        followUp.complete(PipelineSupport.blankToNull(request == null ? null : request.outcome()));
        leadService.record(
                followUp.getLead(),
                PipelineActivityType.FOLLOW_UP_COMPLETED,
                "Follow-up completed",
                "FOLLOW_UP",
                followUp.getId());
        return toResponse(followUp, today());
    }

    @Transactional
    public FollowUpResponse cancel(UUID id, FollowUpOutcomeRequest request) {
        FollowUp followUp = require(id);
        followUp.cancel(PipelineSupport.blankToNull(request == null ? null : request.outcome()));
        return toResponse(followUp, today());
    }

    private void apply(FollowUp followUp, FollowUpRequest request, Lead lead) {
        followUp.setType(request.type());
        followUp.setDueDate(request.dueDate());
        followUp.setDueTime(request.dueTime());
        followUp.setAssignedTo(support.requireAssigned(request.assignedTo()));
        followUp.setNotes(PipelineSupport.blankToNull(request.notes()));
        if (request.customerId() != null && !request.customerId().isBlank()) {
            followUp.setCustomer(customerService.requireActive(PipelineSupport.parseUuid(request.customerId(), "customerId")));
        } else {
            followUp.setCustomer(lead.getConvertedCustomer());
        }
    }

    private FollowUp require(UUID id) {
        return followUpRepository
                .findById(id)
                .orElseThrow(() -> PipelineSupport.notFound(ErrorCodes.FOLLOW_UP_NOT_FOUND, "Follow-up not found"));
    }

    private LocalDate today() {
        String tz = support.currentTenant().getTimezone();
        return LocalDate.now(ZoneId.of(tz == null ? "Asia/Kolkata" : tz));
    }

    private FollowUpResponse toResponse(FollowUp followUp, LocalDate today) {
        Lead lead = followUp.getLead();
        Customer customer = followUp.getCustomer();
        boolean overdue = followUp.isPending() && followUp.getDueDate().isBefore(today);
        return new FollowUpResponse(
                followUp.getId().toString(),
                lead.getId().toString(),
                lead.getName(),
                lead.getLeadNumber(),
                customer == null ? null : customer.getId().toString(),
                followUp.getType(),
                followUp.getDueDate(),
                followUp.getDueTime(),
                followUp.getAssignedTo() == null ? null : followUp.getAssignedTo().toString(),
                support.memberName(followUp.getAssignedTo()),
                followUp.getStatus(),
                followUp.getNotes(),
                followUp.getOutcome(),
                followUp.getCompletedAt(),
                overdue,
                followUp.getCreatedAt(),
                followUp.getUpdatedAt());
    }
}
