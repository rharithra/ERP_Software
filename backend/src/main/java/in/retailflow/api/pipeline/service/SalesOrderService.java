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
import in.retailflow.api.pipeline.domain.QuotationStatus;
import in.retailflow.api.pipeline.domain.SalesOrder;
import in.retailflow.api.pipeline.domain.SalesOrderItem;
import in.retailflow.api.pipeline.domain.SalesOrderNumberCounter;
import in.retailflow.api.pipeline.domain.SalesOrderStatus;
import in.retailflow.api.pipeline.dto.LineItemResponse;
import in.retailflow.api.pipeline.dto.QuotationRequest.LineRequest;
import in.retailflow.api.pipeline.dto.SalesOrderRequest;
import in.retailflow.api.pipeline.dto.SalesOrderResponse;
import in.retailflow.api.pipeline.repository.SalesOrderNumberCounterRepository;
import in.retailflow.api.pipeline.repository.SalesOrderRepository;
import in.retailflow.api.sales.domain.Customer;
import in.retailflow.api.sales.domain.Sale;
import in.retailflow.api.sales.domain.SaleItem;
import in.retailflow.api.sales.domain.SaleMoney;
import in.retailflow.api.sales.dto.SaleResponse;
import in.retailflow.api.sales.service.CustomerService;
import in.retailflow.api.sales.service.SaleService;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderNumberCounterRepository counterRepository;
    private final ProductRepository productRepository;
    private final CustomerService customerService;
    private final QuotationService quotationService;
    private final LeadService leadService;
    private final SaleService saleService;
    private final PipelineSupport support;

    public SalesOrderService(
            SalesOrderRepository salesOrderRepository,
            SalesOrderNumberCounterRepository counterRepository,
            ProductRepository productRepository,
            CustomerService customerService,
            QuotationService quotationService,
            LeadService leadService,
            SaleService saleService,
            PipelineSupport support) {
        this.salesOrderRepository = salesOrderRepository;
        this.counterRepository = counterRepository;
        this.productRepository = productRepository;
        this.customerService = customerService;
        this.quotationService = quotationService;
        this.leadService = leadService;
        this.saleService = saleService;
        this.support = support;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> list(String q, SalesOrderStatus status, UUID customerId, int page, int size) {
        var pageable = PageRequest.of(
                Math.max(page - 1, 0), PipelineSupport.clampSize(size), Sort.by("orderDate").descending());
        return PageResponse.from(salesOrderRepository
                .search(PipelineSupport.blankToNull(q), status, customerId, pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderRequest request) {
        Tenant tenant = support.currentTenant();
        Customer customer = customerService.requireActive(PipelineSupport.parseUuid(request.customerId(), "customerId"));
        SalesOrder order = new SalesOrder(
                UUID.randomUUID(),
                tenant,
                nextNumber(tenant),
                customer,
                request.orderDate(),
                TenantContext.require().userId());
        order.setLead(resolveLead(request.leadId()));
        order.setQuotation(resolveQuotation(request.quotationId()));
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        order.setNotes(PipelineSupport.blankToNull(request.notes()));
        order.replaceItems(buildItems(tenant, order, request.items()), money(request.discount()));
        salesOrderRepository.save(order);
        noteCreated(order);
        return toResponse(order);
    }

    @Transactional
    public SalesOrderResponse createFromQuotation(UUID quotationId) {
        Quotation quotation = quotationService.require(quotationId);
        if (quotation.getStatus() != QuotationStatus.ACCEPTED) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_STATUS, "Accept the quotation before creating a sales order", HttpStatus.CONFLICT.value());
        }
        Tenant tenant = quotation.getTenant();
        SalesOrder order = new SalesOrder(
                UUID.randomUUID(),
                tenant,
                nextNumber(tenant),
                quotation.getCustomer(),
                quotation.getQuotationDate(),
                TenantContext.require().userId());
        order.setQuotation(quotation);
        order.setLead(quotation.getLead());
        order.setNotes(quotation.getNotes());
        List<SalesOrderItem> items = new ArrayList<>();
        for (QuotationItem line : quotation.getItems()) {
            items.add(copy(tenant, order, line));
        }
        order.replaceItems(items, quotation.getDiscount());
        salesOrderRepository.save(order);
        noteCreated(order);
        return toResponse(order);
    }

    @Transactional
    public SalesOrderResponse update(UUID id, SalesOrderRequest request) {
        SalesOrder order = require(id);
        order.requireDraft();
        order.setCustomer(customerService.requireActive(PipelineSupport.parseUuid(request.customerId(), "customerId")));
        order.setLead(resolveLead(request.leadId()));
        order.setQuotation(resolveQuotation(request.quotationId()));
        order.setOrderDate(request.orderDate());
        order.setExpectedDeliveryDate(request.expectedDeliveryDate());
        order.setNotes(PipelineSupport.blankToNull(request.notes()));
        order.replaceItems(buildItems(order.getTenant(), order, request.items()), money(request.discount()));
        return toResponse(order);
    }

    @Transactional
    public SalesOrderResponse confirm(UUID id) {
        SalesOrder order = lock(id);
        order.confirm();
        return toResponse(order);
    }

    @Transactional
    public SalesOrderResponse process(UUID id) {
        SalesOrder order = lock(id);
        order.process();
        return toResponse(order);
    }

    @Transactional
    public SalesOrderResponse ready(UUID id) {
        SalesOrder order = lock(id);
        order.markReady();
        return toResponse(order);
    }

    @Transactional
    public SalesOrderResponse cancel(UUID id) {
        SalesOrder order = lock(id);
        order.cancel();
        return toResponse(order);
    }

    @Transactional
    public SaleResponse convertToSale(UUID id) {
        SalesOrder order = lock(id);
        order.requireConvertible();
        List<SaleItem> saleItems = new ArrayList<>();
        Sale sale = saleService.createDraftFromOrder(order, List.of(), java.math.BigDecimal.ZERO.setScale(2));
        for (SalesOrderItem line : order.getItems()) {
            SaleMoney.LineTotals totals = SaleMoney.line(
                    line.getQuantity(), line.getUnitPrice(), line.getGstRate(), line.getDiscount());
            saleItems.add(new SaleItem(
                    UUID.randomUUID(),
                    order.getTenant(),
                    sale,
                    line.getProduct(),
                    line.getProductNameSnapshot(),
                    line.getSkuSnapshot(),
                    line.getUnitSnapshot(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getGstRate(),
                    line.getDiscount(),
                    totals));
        }
        sale.replaceItems(saleItems, order.getDiscount());
        Sale completed = saleService.completeConvertedSale(sale.getId(), order);
        order.markCompleted(completed);
        if (order.getLead() != null) {
            order.getLead().changeStatus(LeadStatus.WON, null);
            leadService.record(
                    order.getLead(),
                    PipelineActivityType.SALE_CREATED,
                    "Sales order converted to " + completed.getSaleNumber() + " / " + completed.getInvoiceNumber(),
                    "SALE",
                    completed.getId());
            leadService.record(order.getLead(), PipelineActivityType.LEAD_WON, "Lead won", "SALE", completed.getId());
        }
        return saleService.get(completed.getId());
    }

    public SalesOrder require(UUID id) {
        return salesOrderRepository
                .findById(id)
                .orElseThrow(() -> PipelineSupport.notFound(ErrorCodes.SALES_ORDER_NOT_FOUND, "Sales order not found"));
    }

    private SalesOrder lock(UUID id) {
        return salesOrderRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> PipelineSupport.notFound(ErrorCodes.SALES_ORDER_NOT_FOUND, "Sales order not found"));
    }

    private void noteCreated(SalesOrder order) {
        if (order.getLead() != null) {
            order.getLead().advanceAtLeast(LeadStatus.NEGOTIATION);
            leadService.record(
                    order.getLead(),
                    PipelineActivityType.SALES_ORDER_CREATED,
                    "Sales order " + order.getOrderNumber() + " created",
                    "SALES_ORDER",
                    order.getId());
        }
    }

    private List<SalesOrderItem> buildItems(Tenant tenant, SalesOrder order, List<LineRequest> requests) {
        List<SalesOrderItem> items = new ArrayList<>();
        for (LineRequest line : requests) {
            Product product = productRepository
                    .findById(PipelineSupport.parseUuid(line.productId(), "productId"))
                    .orElseThrow(() -> new RetailflowException(
                            ErrorCodes.PRODUCT_NOT_FOUND, "Product not found", HttpStatus.NOT_FOUND.value()));
            if (!product.isActive()) {
                throw new RetailflowException(
                        ErrorCodes.PRODUCT_INACTIVE, "Inactive products cannot be ordered", HttpStatus.CONFLICT.value());
            }
            BigDecimal gst = GstRate.fromPercent(line.gstRate()).percent();
            BigDecimal quantity = line.quantity().setScale(3, RoundingMode.UNNECESSARY);
            BigDecimal unitPrice = line.unitPrice().setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal discount = money(line.discount());
            SaleMoney.LineTotals totals;
            try {
                totals = SaleMoney.line(quantity, unitPrice, gst, discount);
            } catch (IllegalArgumentException ex) {
                throw new RetailflowException(ErrorCodes.VALIDATION_ERROR, ex.getMessage(), HttpStatus.BAD_REQUEST.value());
            }
            items.add(new SalesOrderItem(
                    UUID.randomUUID(),
                    tenant,
                    order,
                    product,
                    product.getName(),
                    product.getSku(),
                    product.getUnit().name(),
                    quantity,
                    unitPrice,
                    gst,
                    discount,
                    totals));
        }
        return items;
    }

    private SalesOrderItem copy(Tenant tenant, SalesOrder order, QuotationItem line) {
        SaleMoney.LineTotals totals = new SaleMoney.LineTotals(
                line.getTaxableAmount(), line.getTaxAmount(), line.getLineTotal());
        return new SalesOrderItem(
                UUID.randomUUID(),
                tenant,
                order,
                line.getProduct(),
                line.getProductNameSnapshot(),
                line.getSkuSnapshot(),
                line.getUnitSnapshot(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getGstRate(),
                line.getDiscount(),
                totals);
    }

    private Lead resolveLead(String leadId) {
        String value = PipelineSupport.blankToNull(leadId);
        return value == null ? null : leadService.require(PipelineSupport.parseUuid(value, "leadId"));
    }

    private Quotation resolveQuotation(String quotationId) {
        String value = PipelineSupport.blankToNull(quotationId);
        return value == null ? null : quotationService.require(PipelineSupport.parseUuid(value, "quotationId"));
    }

    private String nextNumber(Tenant tenant) {
        SalesOrderNumberCounter counter = counterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseGet(() -> {
                    try {
                        counterRepository.saveAndFlush(new SalesOrderNumberCounter(tenant));
                    } catch (DataIntegrityViolationException ignored) {
                    }
                    return counterRepository
                            .findByTenantIdForUpdate(tenant.getId())
                            .orElseThrow(() -> new RetailflowException(
                                    ErrorCodes.INTERNAL_ERROR,
                                    "Unable to allocate a sales order number",
                                    HttpStatus.INTERNAL_SERVER_ERROR.value()));
                });
        return DocumentNumbers.format("SO-", counter.nextValue());
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private SalesOrderResponse toResponse(SalesOrder order) {
        List<LineItemResponse> items = order.getItems().stream()
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
        Sale sale = order.getSale();
        return new SalesOrderResponse(
                order.getId().toString(),
                order.getOrderNumber(),
                order.getQuotation() == null ? null : order.getQuotation().getId().toString(),
                order.getQuotation() == null ? null : order.getQuotation().getQuotationNumber(),
                order.getLead() == null ? null : order.getLead().getId().toString(),
                order.getCustomer().getId().toString(),
                order.getCustomer().getName(),
                sale == null ? null : sale.getId().toString(),
                sale == null ? null : sale.getSaleNumber(),
                sale == null ? null : sale.getInvoiceNumber(),
                order.getOrderDate(),
                order.getExpectedDeliveryDate(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getSubtotal(),
                order.getDiscount(),
                order.getTaxTotal(),
                order.getGrandTotal(),
                order.getAdvancePaid(),
                order.getOutstandingAmount(),
                order.getNotes(),
                order.getCreatedBy().toString(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items.size(),
                items);
    }
}
