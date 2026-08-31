package in.retailflow.api.pipeline.service;

import in.retailflow.api.common.DocumentNumbers;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.pipeline.domain.Lead;
import in.retailflow.api.pipeline.domain.LeadNumberCounter;
import in.retailflow.api.pipeline.domain.LeadPriority;
import in.retailflow.api.pipeline.domain.LeadStatus;
import in.retailflow.api.pipeline.domain.PipelineActivity;
import in.retailflow.api.pipeline.domain.PipelineActivityType;
import in.retailflow.api.pipeline.dto.LeadRequest;
import in.retailflow.api.pipeline.dto.LeadResponse;
import in.retailflow.api.pipeline.dto.LeadStatusRequest;
import in.retailflow.api.pipeline.dto.PipelineActivityResponse;
import in.retailflow.api.pipeline.repository.LeadNumberCounterRepository;
import in.retailflow.api.pipeline.repository.LeadRepository;
import in.retailflow.api.pipeline.repository.PipelineActivityRepository;
import in.retailflow.api.sales.domain.Customer;
import in.retailflow.api.sales.dto.CustomerRequest;
import in.retailflow.api.sales.dto.CustomerResponse;
import in.retailflow.api.sales.repository.CustomerRepository;
import in.retailflow.api.sales.service.CustomerService;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadNumberCounterRepository counterRepository;
    private final PipelineActivityRepository activityRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final PipelineSupport support;

    public LeadService(
            LeadRepository leadRepository,
            LeadNumberCounterRepository counterRepository,
            PipelineActivityRepository activityRepository,
            CustomerRepository customerRepository,
            CustomerService customerService,
            PipelineSupport support) {
        this.leadRepository = leadRepository;
        this.counterRepository = counterRepository;
        this.activityRepository = activityRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.support = support;
    }

    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> list(String q, LeadStatus status, String priority, int page, int size) {
        var pageable = PageRequest.of(
                Math.max(page - 1, 0), PipelineSupport.clampSize(size), Sort.by("updatedAt").descending());
        return PageResponse.from(leadRepository
                .search(PipelineSupport.blankToNull(q), status, PipelineSupport.blankToNull(priority), pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<LeadResponse> board() {
        return leadRepository.findAllByOrderByUpdatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LeadResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public List<PipelineActivityResponse> timeline(UUID leadId) {
        require(leadId);
        return activityRepository.findByLeadIdOrderByCreatedAtAsc(leadId).stream()
                .map(a -> new PipelineActivityResponse(
                        a.getId().toString(),
                        a.getActivityType(),
                        a.getMessage(),
                        a.getReferenceType(),
                        a.getReferenceId() == null ? null : a.getReferenceId().toString(),
                        a.getCreatedAt()))
                .toList();
    }

    @Transactional
    public LeadResponse create(LeadRequest request) {
        Tenant tenant = support.currentTenant();
        Lead lead = new Lead(UUID.randomUUID(), tenant, nextNumber(tenant), request.name().trim());
        apply(lead, request);
        leadRepository.save(lead);
        record(lead, PipelineActivityType.LEAD_CREATED, "Lead created", null, null);
        return toResponse(lead);
    }

    @Transactional
    public LeadResponse update(UUID id, LeadRequest request) {
        Lead lead = require(id);
        if (lead.getStatus() == LeadStatus.LOST || lead.getStatus() == LeadStatus.WON) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_STATUS, "Closed leads cannot be edited", HttpStatus.CONFLICT.value());
        }
        lead.setName(request.name().trim());
        apply(lead, request);
        return toResponse(lead);
    }

    @Transactional
    public LeadResponse changeStatus(UUID id, LeadStatusRequest request) {
        Lead lead = require(id);
        if (request.status() == LeadStatus.LOST && (request.lostReason() == null || request.lostReason().isBlank())) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR, "A lost reason is required", HttpStatus.BAD_REQUEST.value());
        }
        lead.changeStatus(request.status(), PipelineSupport.blankToNull(request.lostReason()));
        if (request.status() == LeadStatus.LOST) {
            record(lead, PipelineActivityType.LEAD_LOST, "Lead marked lost: " + lead.getLostReason(), null, null);
        }
        if (request.status() == LeadStatus.WON) {
            record(lead, PipelineActivityType.LEAD_WON, "Lead marked won", null, null);
        }
        return toResponse(lead);
    }

    @Transactional
    public CustomerResponse convertToCustomer(UUID id) {
        Lead lead = require(id);
        if (lead.getConvertedCustomer() != null) {
            return customerService.get(lead.getConvertedCustomer().getId());
        }
        Customer customer = null;
        if (lead.getPhone() != null && !lead.getPhone().isBlank()) {
            customer = customerRepository.findFirstByPhone(lead.getPhone().trim()).orElse(null);
        }
        if (customer == null) {
            CustomerResponse created = customerService.create(new CustomerRequest(
                    lead.getName(), lead.getPhone(), lead.getEmail(), lead.getAddress(), null, lead.getNotes()));
            customer = customerRepository.findById(UUID.fromString(created.id())).orElseThrow();
        }
        lead.markConverted(customer);
        record(
                lead,
                PipelineActivityType.LEAD_CONVERTED,
                "Converted to customer " + customer.getName(),
                "CUSTOMER",
                customer.getId());
        return customerService.get(customer.getId());
    }

    public Lead require(UUID id) {
        return leadRepository
                .findById(id)
                .orElseThrow(() -> PipelineSupport.notFound(ErrorCodes.LEAD_NOT_FOUND, "Lead not found"));
    }

    public void record(Lead lead, PipelineActivityType type, String message, String referenceType, UUID referenceId) {
        activityRepository.save(new PipelineActivity(
                UUID.randomUUID(),
                lead.getTenant(),
                lead,
                type,
                message,
                referenceType,
                referenceId,
                TenantContext.require().userId()));
    }

    private void apply(Lead lead, LeadRequest request) {
        lead.setPhone(PipelineSupport.blankToNull(request.phone()));
        lead.setEmail(PipelineSupport.blankToNull(request.email()));
        lead.setCompanyName(PipelineSupport.blankToNull(request.companyName()));
        lead.setAddress(PipelineSupport.blankToNull(request.address()));
        lead.setSource(request.source());
        lead.setRequirement(PipelineSupport.blankToNull(request.requirement()));
        lead.setExpectedValue(request.expectedValue());
        lead.setExpectedCloseDate(request.expectedCloseDate());
        lead.setAssignedTo(support.requireAssigned(request.assignedTo()));
        lead.setPriority(request.priority() == null ? LeadPriority.MEDIUM : request.priority());
        lead.setNotes(PipelineSupport.blankToNull(request.notes()));
    }

    private String nextNumber(Tenant tenant) {
        LeadNumberCounter counter = counterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseGet(() -> {
                    try {
                        counterRepository.saveAndFlush(new LeadNumberCounter(tenant));
                    } catch (DataIntegrityViolationException ignored) {
                    }
                    return counterRepository
                            .findByTenantIdForUpdate(tenant.getId())
                            .orElseThrow(() -> new RetailflowException(
                                    ErrorCodes.INTERNAL_ERROR,
                                    "Unable to allocate a lead number",
                                    HttpStatus.INTERNAL_SERVER_ERROR.value()));
                });
        return DocumentNumbers.format("LEAD-", counter.nextValue());
    }

    private LeadResponse toResponse(Lead lead) {
        Customer converted = lead.getConvertedCustomer();
        return new LeadResponse(
                lead.getId().toString(),
                lead.getLeadNumber(),
                lead.getName(),
                lead.getPhone(),
                lead.getEmail(),
                lead.getCompanyName(),
                lead.getAddress(),
                lead.getSource(),
                lead.getRequirement(),
                lead.getExpectedValue(),
                lead.getExpectedCloseDate(),
                lead.getAssignedTo() == null ? null : lead.getAssignedTo().toString(),
                support.memberName(lead.getAssignedTo()),
                lead.getPriority(),
                lead.getStatus(),
                lead.getNotes(),
                lead.getLostReason(),
                converted == null ? null : converted.getId().toString(),
                converted == null ? null : converted.getName(),
                lead.getCreatedAt(),
                lead.getUpdatedAt());
    }
}
