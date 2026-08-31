package in.retailflow.api.pipeline.service;

import in.retailflow.api.catalog.domain.GstRate;
import in.retailflow.api.catalog.domain.Product;
import in.retailflow.api.catalog.repository.ProductRepository;
import in.retailflow.api.common.DocumentNumbers;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.pipeline.domain.Lead;
import in.retailflow.api.pipeline.domain.LeadStatus;
import in.retailflow.api.pipeline.domain.PipelineActivityType;
import in.retailflow.api.pipeline.domain.Quotation;
import in.retailflow.api.pipeline.domain.QuotationItem;
import in.retailflow.api.pipeline.domain.QuotationNumberCounter;
import in.retailflow.api.pipeline.domain.QuotationStatus;
import in.retailflow.api.pipeline.dto.LineItemResponse;
import in.retailflow.api.pipeline.dto.QuotationRequest;
import in.retailflow.api.pipeline.dto.QuotationRequest.LineRequest;
import in.retailflow.api.pipeline.dto.QuotationResponse;
import in.retailflow.api.pipeline.repository.QuotationNumberCounterRepository;
import in.retailflow.api.pipeline.repository.QuotationRepository;
import in.retailflow.api.sales.domain.Customer;
import in.retailflow.api.sales.domain.SaleMoney;
import in.retailflow.api.sales.service.CustomerService;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final QuotationNumberCounterRepository counterRepository;
    private final ProductRepository productRepository;
    private final CustomerService customerService;
    private final LeadService leadService;
    private final PipelineSupport support;
    private final NotificationService notificationService;

    public QuotationService(
            QuotationRepository quotationRepository,
            QuotationNumberCounterRepository counterRepository,
            ProductRepository productRepository,
            CustomerService customerService,
            LeadService leadService,
            PipelineSupport support,
            NotificationService notificationService) {
        this.quotationRepository = quotationRepository;
        this.counterRepository = counterRepository;
        this.productRepository = productRepository;
        this.customerService = customerService;
        this.leadService = leadService;
        this.support = support;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<QuotationResponse> list(String q, QuotationStatus status, UUID customerId, int page, int size) {
        expireSent();
        var pageable = PageRequest.of(
                Math.max(page - 1, 0), PipelineSupport.clampSize(size), Sort.by("quotationDate").descending());
        return PageResponse.from(quotationRepository
                .search(PipelineSupport.blankToNull(q), status, customerId, pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public QuotationResponse get(UUID id) {
        expireSent();
        return toResponse(require(id));
    }

    @Transactional
    public QuotationResponse create(QuotationRequest request) {
        Tenant tenant = support.currentTenant();
        Customer customer = customerService.requireActive(PipelineSupport.parseUuid(request.customerId(), "customerId"));
        Quotation quotation = new Quotation(
                UUID.randomUUID(),
                tenant,
                nextNumber(tenant),
                customer,
                request.quotationDate(),
                request.validUntil(),
                TenantContext.require().userId());
        quotation.setLead(resolveLead(request.leadId()));
        quotation.setNotes(PipelineSupport.blankToNull(request.notes()));
        quotation.setTermsAndConditions(PipelineSupport.blankToNull(request.termsAndConditions()));
        quotation.replaceItems(buildItems(tenant, quotation, request.items()), money(request.discount()));
        quotationRepository.save(quotation);
        if (quotation.getLead() != null) {
            quotation.getLead().advanceAtLeast(LeadStatus.QUOTATION);
            leadService.record(
                    quotation.getLead(),
                    PipelineActivityType.QUOTATION_CREATED,
                    "Quotation " + quotation.getQuotationNumber() + " created",
                    "QUOTATION",
                    quotation.getId());
        }
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse update(UUID id, QuotationRequest request) {
        Quotation quotation = require(id);
        quotation.requireDraft();
        quotation.setCustomer(customerService.requireActive(PipelineSupport.parseUuid(request.customerId(), "customerId")));
        quotation.setLead(resolveLead(request.leadId()));
        quotation.setQuotationDate(request.quotationDate());
        quotation.setValidUntil(request.validUntil());
        quotation.setNotes(PipelineSupport.blankToNull(request.notes()));
        quotation.setTermsAndConditions(PipelineSupport.blankToNull(request.termsAndConditions()));
        quotation.replaceItems(buildItems(quotation.getTenant(), quotation, request.items()), money(request.discount()));
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse markSent(UUID id) {
        Quotation quotation = require(id);
        quotation.markSent();
        if (quotation.getLead() != null) {
            quotation.getLead().advanceAtLeast(LeadStatus.QUOTATION);
            leadService.record(
                    quotation.getLead(),
                    PipelineActivityType.QUOTATION_SENT,
                    "Quotation " + quotation.getQuotationNumber() + " marked sent",
                    "QUOTATION",
                    quotation.getId());
        }
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse accept(UUID id) {
        expireSent();
        Quotation quotation = require(id);
        quotation.markAccepted();
        if (quotation.getLead() != null) {
            quotation.getLead().advanceAtLeast(LeadStatus.NEGOTIATION);
            leadService.record(
                    quotation.getLead(),
                    PipelineActivityType.QUOTATION_ACCEPTED,
                    "Quotation " + quotation.getQuotationNumber() + " accepted",
                    "QUOTATION",
                    quotation.getId());
        }
        notificationService.quotationAccepted(quotation);
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse reject(UUID id) {
        Quotation quotation = require(id);
        quotation.markRejected();
        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse cancel(UUID id) {
        Quotation quotation = require(id);
        quotation.markCancelled();
        return toResponse(quotation);
    }

    public Quotation require(UUID id) {
        return quotationRepository
                .findById(id)
                .orElseThrow(() -> PipelineSupport.notFound(ErrorCodes.QUOTATION_NOT_FOUND, "Quotation not found"));
    }

    public List<QuotationItem> buildItems(Tenant tenant, Quotation quotation, List<LineRequest> requests) {
        List<QuotationItem> items = new ArrayList<>();
        for (LineRequest line : requests) {
            Product product = requireProduct(PipelineSupport.parseUuid(line.productId(), "productId"));
            if (!product.isActive()) {
                throw new RetailflowException(
                        ErrorCodes.PRODUCT_INACTIVE, "Inactive products cannot be quoted", HttpStatus.CONFLICT.value());
            }
            BigDecimal gst = parseGst(line.gstRate());
            BigDecimal quantity = line.quantity().setScale(3, RoundingMode.UNNECESSARY);
            BigDecimal unitPrice = line.unitPrice().setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal discount = money(line.discount());
            SaleMoney.LineTotals totals;
            try {
                totals = SaleMoney.line(quantity, unitPrice, gst, discount);
            } catch (IllegalArgumentException ex) {
                throw new RetailflowException(ErrorCodes.VALIDATION_ERROR, ex.getMessage(), HttpStatus.BAD_REQUEST.value());
            }
            items.add(new QuotationItem(
                    UUID.randomUUID(), tenant, quotation, product, quantity, unitPrice, gst, discount, totals));
        }
        return items;
    }

    private void expireSent() {
        LocalDate today = LocalDate.now(ZoneId.of(
                support.currentTenant().getTimezone() == null ? "Asia/Kolkata" : support.currentTenant().getTimezone()));
        for (Quotation quotation : quotationRepository.findByStatusAndValidUntilLessThanEqual(QuotationStatus.SENT, today.minusDays(1))) {
            quotation.expireIfNeeded(today);
        }
    }

    private Lead resolveLead(String leadId) {
        String value = PipelineSupport.blankToNull(leadId);
        if (value == null) {
            return null;
        }
        return leadService.require(PipelineSupport.parseUuid(value, "leadId"));
    }

    private Product requireProduct(UUID productId) {
        return productRepository
                .findById(productId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.PRODUCT_NOT_FOUND, "Product not found", HttpStatus.NOT_FOUND.value()));
    }

    private BigDecimal parseGst(BigDecimal gstRate) {
        try {
            return GstRate.fromPercent(gstRate).percent();
        } catch (IllegalArgumentException ex) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR, "GST rate must be 0, 5, 12, 18, or 28 percent", HttpStatus.BAD_REQUEST.value());
        }
    }

    private String nextNumber(Tenant tenant) {
        QuotationNumberCounter counter = counterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseGet(() -> {
                    try {
                        counterRepository.saveAndFlush(new QuotationNumberCounter(tenant));
                    } catch (DataIntegrityViolationException ignored) {
                    }
                    return counterRepository
                            .findByTenantIdForUpdate(tenant.getId())
                            .orElseThrow(() -> new RetailflowException(
                                    ErrorCodes.INTERNAL_ERROR,
                                    "Unable to allocate a quotation number",
                                    HttpStatus.INTERNAL_SERVER_ERROR.value()));
                });
        return DocumentNumbers.format("QT-", counter.nextValue());
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private QuotationResponse toResponse(Quotation quotation) {
        List<LineItemResponse> items = quotation.getItems().stream()
                .map(item -> new LineItemResponse(
                        item.getId().toString(),
                        item.getProduct().getId().toString(),
                        item.getProductNameSnapshot(),
                        item.getSkuSnapshot(),
                        item.getUnitSnapshot(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getGstRate(),
                        item.getDiscount(),
                        item.getTaxableAmount(),
                        item.getTaxAmount(),
                        item.getLineTotal()))
                .toList();
        return new QuotationResponse(
                quotation.getId().toString(),
                quotation.getQuotationNumber(),
                quotation.getLead() == null ? null : quotation.getLead().getId().toString(),
                quotation.getLead() == null ? null : quotation.getLead().getName(),
                quotation.getCustomer().getId().toString(),
                quotation.getCustomer().getName(),
                quotation.getQuotationDate(),
                quotation.getValidUntil(),
                quotation.getStatus(),
                quotation.getSubtotal(),
                quotation.getDiscount(),
                quotation.getTaxTotal(),
                quotation.getGrandTotal(),
                quotation.getNotes(),
                quotation.getTermsAndConditions(),
                quotation.getCreatedBy().toString(),
                quotation.getCreatedAt(),
                quotation.getUpdatedAt(),
                items.size(),
                items);
    }
}
