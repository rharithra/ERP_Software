package in.retailflow.api.sales.service;

import in.retailflow.api.catalog.domain.GstRate;
import in.retailflow.api.catalog.domain.Product;
import in.retailflow.api.catalog.repository.ProductRepository;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.identity.domain.UserAccount;
import in.retailflow.api.identity.repository.UserAccountRepository;
import in.retailflow.api.inventory.service.InventoryService;
import in.retailflow.api.sales.domain.Customer;
import in.retailflow.api.sales.domain.InvoiceNumberCounter;
import in.retailflow.api.sales.domain.PaymentMethod;
import in.retailflow.api.sales.domain.Sale;
import in.retailflow.api.sales.domain.SaleItem;
import in.retailflow.api.sales.domain.SaleMoney;
import in.retailflow.api.sales.domain.SaleNumberCounter;
import in.retailflow.api.sales.domain.SaleStatus;
import in.retailflow.api.sales.dto.CompleteSaleRequest;
import in.retailflow.api.sales.dto.SaleDashboardResponse;
import in.retailflow.api.sales.dto.SaleInvoiceResponse;
import in.retailflow.api.sales.dto.SaleItemResponse;
import in.retailflow.api.sales.dto.SaleRequest;
import in.retailflow.api.sales.dto.SaleRequest.SaleItemRequest;
import in.retailflow.api.sales.dto.SaleResponse;
import in.retailflow.api.sales.dto.SaleSummaryResponse;
import in.retailflow.api.sales.repository.InvoiceNumberCounterRepository;
import in.retailflow.api.sales.repository.SaleNumberCounterRepository;
import in.retailflow.api.sales.repository.SaleRepository;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import in.retailflow.api.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleNumberCounterRepository saleCounterRepository;
    private final InvoiceNumberCounterRepository invoiceCounterRepository;
    private final CustomerService customerService;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final PaymentService paymentService;

    public SaleService(
            SaleRepository saleRepository,
            SaleNumberCounterRepository saleCounterRepository,
            InvoiceNumberCounterRepository invoiceCounterRepository,
            CustomerService customerService,
            ProductRepository productRepository,
            InventoryService inventoryService,
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            PaymentService paymentService) {
        this.saleRepository = saleRepository;
        this.saleCounterRepository = saleCounterRepository;
        this.invoiceCounterRepository = invoiceCounterRepository;
        this.customerService = customerService;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.paymentService = paymentService;
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleResponse> list(
            String query, UUID customerId, SaleStatus status, LocalDate fromDate, LocalDate toDate, int page, int size) {
        var pageable = PageRequest.of(
                Math.max(page - 1, 0),
                clampSize(size),
                Sort.by("saleDate").descending().and(Sort.by("saleNumber").descending()));
        return PageResponse.from(saleRepository
                .search(blankToNull(query), customerId, status == null ? null : status.name(), fromDate, toDate, pageable)
                .map(sale -> toResponse(sale, false)));
    }

    @Transactional(readOnly = true)
    public SaleSummaryResponse summary() {
        Object[] row = saleRepository.summarize();
        Object[] values = row.length >= 4 && !(row[0] instanceof Object[]) ? row : (Object[]) row[0];
        return new SaleSummaryResponse(asLong(values[0]), asLong(values[1]), asLong(values[2]), asMoney(values[3]));
    }

    @Transactional(readOnly = true)
    public SaleDashboardResponse dashboard() {
        Tenant tenant = currentTenant();
        LocalDate today = LocalDate.now(ZoneId.of(tenant.getTimezone() == null ? "Asia/Kolkata" : tenant.getTimezone()));
        Object[] row = saleRepository.summarizeToday(today);
        Object[] values = row.length >= 2 && !(row[0] instanceof Object[]) ? row : (Object[]) row[0];
        List<SaleResponse> recent = saleRepository.findRecentCompleted(PageRequest.of(0, 5)).stream()
                .map(sale -> toResponse(sale, false))
                .toList();
        return new SaleDashboardResponse(asLong(values[0]), asMoney(values[1]), recent);
    }

    @Transactional(readOnly = true)
    public SaleResponse get(UUID id) {
        return toResponse(require(id), true);
    }

    @Transactional(readOnly = true)
    public SaleInvoiceResponse invoice(UUID id) {
        Sale sale = require(id);
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new RetailflowException(
                    ErrorCodes.SALE_NOT_COMPLETED,
                    "Invoice is available after the sale is completed",
                    HttpStatus.CONFLICT.value());
        }
        return new SaleInvoiceResponse(
                toResponse(sale, true),
                sale.getCompanyName(),
                sale.getCompanyGstin(),
                sale.getCompanyAddress(),
                sale.getCompanyPhone());
    }

    @Transactional
    public SaleResponse create(SaleRequest request) {
        Tenant tenant = currentTenant();
        Sale sale = new Sale(
                UUID.randomUUID(),
                tenant,
                nextSaleNumber(tenant),
                request.saleDate(),
                blankToNull(request.notes()),
                TenantContext.require().userId());
        sale.assignCustomer(resolveCustomer(request.customerId()));
        sale.replaceItems(buildItems(tenant, sale, request.items()), saleDiscount(request.discount()));
        return toResponse(saleRepository.save(sale), true);
    }

    @Transactional
    public SaleResponse update(UUID id, SaleRequest request) {
        Sale sale = requireDraft(id);
        Tenant tenant = sale.getTenant();
        sale.assignCustomer(resolveCustomer(request.customerId()));
        sale.setSaleDate(request.saleDate());
        sale.setNotes(blankToNull(request.notes()));
        sale.replaceItems(buildItems(tenant, sale, request.items()), saleDiscount(request.discount()));
        return toResponse(sale, true);
    }

    @Transactional
    public SaleResponse complete(UUID id, CompleteSaleRequest request) {
        Sale sale = lockDraft(id);
        if (sale.getItems().isEmpty()) {
            throw new RetailflowException(
                    ErrorCodes.SALE_EMPTY, "Add at least one product before completing this sale", HttpStatus.BAD_REQUEST.value());
        }
        sale.recalculateTotals();
        List<SaleItem> ordered = sale.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        for (SaleItem item : ordered) {
            inventoryService.applySale(item.getProduct().getId(), item.getQuantity(), sale.getId());
        }
        Tenant tenant = sale.getTenant();
        sale.markCompleted(
                nextInvoiceNumber(tenant),
                request.paymentMethod(),
                Instant.now(),
                companyName(tenant),
                tenant.getGstin(),
                companyAddress(tenant),
                tenant.getPhone());
        if (sale.getSalesOrderId() == null) {
            paymentService.recordPosCompletion(sale, sale.getGrandTotal(), request.paymentMethod(), sale.getSaleDate());
        } else {
            paymentService.applySaleTotals(sale);
        }
        return toResponse(sale, true);
    }

    @Transactional
    public Sale createDraftFromOrder(
            in.retailflow.api.pipeline.domain.SalesOrder order, List<SaleItem> items, java.math.BigDecimal discount) {
        Tenant tenant = order.getTenant();
        Sale sale = new Sale(
                UUID.randomUUID(),
                tenant,
                nextSaleNumber(tenant),
                order.getOrderDate(),
                order.getNotes(),
                TenantContext.require().userId());
        sale.setSalesOrderId(order.getId());
        sale.assignCustomer(order.getCustomer());
        sale.replaceItems(items, discount);
        return saleRepository.save(sale);
    }

    @Transactional
    public Sale completeConvertedSale(UUID saleId, in.retailflow.api.pipeline.domain.SalesOrder order) {
        Sale sale = lockDraft(saleId);
        if (sale.getItems().isEmpty()) {
            throw new RetailflowException(
                    ErrorCodes.SALE_EMPTY, "Add at least one product before completing this sale", HttpStatus.BAD_REQUEST.value());
        }
        sale.recalculateTotals();
        List<SaleItem> ordered = sale.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        for (SaleItem item : ordered) {
            inventoryService.applySale(item.getProduct().getId(), item.getQuantity(), sale.getId());
        }
        Tenant tenant = sale.getTenant();
        in.retailflow.api.sales.domain.PaymentMethod method = requestMethodFromOrder(order);
        sale.markCompleted(
                nextInvoiceNumber(tenant),
                method,
                Instant.now(),
                companyName(tenant),
                tenant.getGstin(),
                companyAddress(tenant),
                tenant.getPhone());
        paymentService.attachOrderPaymentsToSale(order, sale);
        return sale;
    }

    private in.retailflow.api.sales.domain.PaymentMethod requestMethodFromOrder(
            in.retailflow.api.pipeline.domain.SalesOrder order) {
        var payments = paymentService.forOrder(order.getId());
        if (!payments.isEmpty()) {
            return payments.getLast().paymentMethod();
        }
        return in.retailflow.api.sales.domain.PaymentMethod.CASH;
    }

    @Transactional
    public SaleResponse cancel(UUID id) {
        Sale sale = lockDraft(id);
        sale.markCancelled();
        return toResponse(sale, true);
    }

    private Sale lockDraft(UUID id) {
        Sale sale = saleRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.SALE_NOT_FOUND, "Sale not found", HttpStatus.NOT_FOUND.value()));
        requireDraftStatus(sale);
        sale.getItems().size();
        return sale;
    }

    private Sale require(UUID id) {
        return saleRepository
                .findById(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.SALE_NOT_FOUND, "Sale not found", HttpStatus.NOT_FOUND.value()));
    }

    private Sale requireDraft(UUID id) {
        Sale sale = require(id);
        requireDraftStatus(sale);
        return sale;
    }

    private void requireDraftStatus(Sale sale) {
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new RetailflowException(
                    ErrorCodes.SALE_NOT_DRAFT,
                    sale.getStatus() == SaleStatus.COMPLETED
                            ? "This sale has already been completed and cannot be changed"
                            : "Only draft sales can be changed",
                    HttpStatus.CONFLICT.value());
        }
    }

    private Customer resolveCustomer(String customerId) {
        String value = blankToNull(customerId);
        if (value == null) {
            return null;
        }
        return customerService.requireActive(parseUuid(value, "customerId"));
    }

    private List<SaleItem> buildItems(Tenant tenant, Sale sale, List<SaleItemRequest> requests) {
        List<SaleItem> items = new ArrayList<>();
        for (SaleItemRequest line : requests) {
            Product product = requireProduct(parseUuid(line.productId(), "productId"));
            if (!product.isActive()) {
                throw new RetailflowException(
                        ErrorCodes.PRODUCT_INACTIVE,
                        "Inactive products cannot be added to a new sale",
                        HttpStatus.CONFLICT.value());
            }
            GstRate gst = parseGst(line.gstRate());
            BigDecimal quantity = line.quantity().setScale(3, java.math.RoundingMode.UNNECESSARY);
            BigDecimal unitPrice = line.unitPrice().setScale(2, java.math.RoundingMode.UNNECESSARY);
            BigDecimal lineDiscount = saleDiscount(line.discount());
            SaleMoney.LineTotals totals;
            try {
                totals = SaleMoney.line(quantity, unitPrice, gst.percent(), lineDiscount);
            } catch (IllegalArgumentException ex) {
                throw new RetailflowException(ErrorCodes.VALIDATION_ERROR, ex.getMessage(), HttpStatus.BAD_REQUEST.value());
            }
            items.add(new SaleItem(
                    UUID.randomUUID(), tenant, sale, product, quantity, unitPrice, gst.percent(), lineDiscount, totals));
        }
        return items;
    }

    private String nextSaleNumber(Tenant tenant) {
        SaleNumberCounter counter = saleCounterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseGet(() -> createSaleCounter(tenant));
        return "SAL-" + String.format("%06d", counter.nextValue());
    }

    private String nextInvoiceNumber(Tenant tenant) {
        InvoiceNumberCounter counter = invoiceCounterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseGet(() -> createInvoiceCounter(tenant));
        return "INV-" + String.format("%06d", counter.nextValue());
    }

    private SaleNumberCounter createSaleCounter(Tenant tenant) {
        try {
            saleCounterRepository.saveAndFlush(new SaleNumberCounter(tenant));
        } catch (DataIntegrityViolationException ignored) {
            // another transaction inserted the counter
        }
        return saleCounterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.INTERNAL_ERROR,
                        "Unable to allocate a sale number",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    private InvoiceNumberCounter createInvoiceCounter(Tenant tenant) {
        try {
            invoiceCounterRepository.saveAndFlush(new InvoiceNumberCounter(tenant));
        } catch (DataIntegrityViolationException ignored) {
            // another transaction inserted the counter
        }
        return invoiceCounterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.INTERNAL_ERROR,
                        "Unable to allocate an invoice number",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    private Product requireProduct(UUID productId) {
        return productRepository
                .findById(productId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.PRODUCT_NOT_FOUND, "Product not found", HttpStatus.NOT_FOUND.value()));
    }

    private GstRate parseGst(BigDecimal gstRate) {
        try {
            return GstRate.fromPercent(gstRate);
        } catch (IllegalArgumentException ex) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR,
                    "GST rate must be 0, 5, 12, 18, or 28 percent",
                    HttpStatus.BAD_REQUEST.value());
        }
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new RetailflowException(ErrorCodes.VALIDATION_ERROR, "Invalid " + field, HttpStatus.BAD_REQUEST.value());
        }
    }

    private Tenant currentTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.TENANT_NOT_FOUND, "Company not found", HttpStatus.NOT_FOUND.value()));
    }

    private SaleResponse toResponse(Sale sale, boolean includeItems) {
        String createdByName = userAccountRepository
                .findById(sale.getCreatedBy())
                .map(UserAccount::getFullName)
                .orElse("Staff");
        List<SaleItemResponse> items =
                includeItems ? sale.getItems().stream().map(this::toItem).toList() : List.of();
        int itemCount = includeItems ? items.size() : sale.getItemCount();
        java.math.BigDecimal paid = java.math.BigDecimal.ZERO.setScale(2);
        java.math.BigDecimal outstanding = sale.getGrandTotal();
        if (sale.getStatus() == SaleStatus.COMPLETED) {
            paid = paymentService.paidForSale(sale);
            outstanding = sale.getGrandTotal().subtract(paid);
        }
        return new SaleResponse(
                sale.getId().toString(),
                sale.getSaleNumber(),
                sale.getInvoiceNumber(),
                sale.getCustomer() == null ? null : sale.getCustomer().getId().toString(),
                sale.getCustomerName(),
                sale.getCustomerPhone(),
                sale.getCustomerGstin(),
                sale.getSaleDate(),
                sale.getStatus(),
                sale.getSubtotal(),
                sale.getDiscount(),
                sale.getTaxTotal(),
                sale.getGrandTotal(),
                sale.getPaymentMethod(),
                sale.getPaymentStatus(),
                sale.getNotes(),
                sale.getCompletedAt(),
                sale.getCreatedBy().toString(),
                createdByName,
                sale.getCreatedAt(),
                sale.getUpdatedAt(),
                itemCount,
                items,
                sale.getSalesOrderId() == null ? null : sale.getSalesOrderId().toString(),
                paid,
                outstanding);
    }

    private SaleItemResponse toItem(SaleItem item) {
        return new SaleItemResponse(
                item.getId().toString(),
                item.getProduct().getId().toString(),
                item.getProductName(),
                item.getSku(),
                item.getUnit(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getGstRate(),
                item.getDiscount(),
                item.getTaxableAmount(),
                item.getTaxAmount(),
                item.getLineTotal());
    }

    private static BigDecimal saleDiscount(BigDecimal discount) {
        if (discount == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return discount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String companyName(Tenant tenant) {
        if (tenant.getLegalName() != null && !tenant.getLegalName().isBlank()) {
            return tenant.getLegalName();
        }
        return tenant.getName();
    }

    private static String companyAddress(Tenant tenant) {
        return Stream.of(
                        tenant.getAddressLine1(),
                        tenant.getAddressLine2(),
                        tenant.getCity(),
                        tenant.getState(),
                        tenant.getPincode())
                .filter(part -> part != null && !part.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
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
