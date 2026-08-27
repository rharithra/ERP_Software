package in.retailflow.api.procurement.service;

import in.retailflow.api.catalog.domain.GstRate;
import in.retailflow.api.catalog.domain.Product;
import in.retailflow.api.catalog.repository.ProductRepository;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.identity.domain.UserAccount;
import in.retailflow.api.identity.repository.UserAccountRepository;
import in.retailflow.api.inventory.service.InventoryService;
import in.retailflow.api.procurement.domain.Purchase;
import in.retailflow.api.procurement.domain.PurchaseItem;
import in.retailflow.api.procurement.domain.PurchaseMoney;
import in.retailflow.api.procurement.domain.PurchaseNumberCounter;
import in.retailflow.api.procurement.domain.PurchaseStatus;
import in.retailflow.api.procurement.domain.Supplier;
import in.retailflow.api.procurement.dto.PurchaseItemResponse;
import in.retailflow.api.procurement.dto.PurchaseRequest;
import in.retailflow.api.procurement.dto.PurchaseRequest.PurchaseItemRequest;
import in.retailflow.api.procurement.dto.PurchaseResponse;
import in.retailflow.api.procurement.dto.PurchaseSummaryResponse;
import in.retailflow.api.procurement.repository.PurchaseNumberCounterRepository;
import in.retailflow.api.procurement.repository.PurchaseRepository;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import in.retailflow.api.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseNumberCounterRepository counterRepository;
    private final SupplierService supplierService;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;

    public PurchaseService(
            PurchaseRepository purchaseRepository,
            PurchaseNumberCounterRepository counterRepository,
            SupplierService supplierService,
            ProductRepository productRepository,
            InventoryService inventoryService,
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository) {
        this.purchaseRepository = purchaseRepository;
        this.counterRepository = counterRepository;
        this.supplierService = supplierService;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseResponse> list(
            String query, UUID supplierId, PurchaseStatus status, LocalDate fromDate, LocalDate toDate, int page, int size) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), clampSize(size), Sort.by("purchaseDate").descending()
                .and(Sort.by("purchaseNumber").descending()));
        return PageResponse.from(purchaseRepository
                .search(blankToNull(query), supplierId, status == null ? null : status.name(), fromDate, toDate, pageable)
                .map(purchase -> toResponse(purchase, false)));
    }

    @Transactional(readOnly = true)
    public PurchaseSummaryResponse summary() {
        Object[] row = purchaseRepository.summarize();
        Object[] values = row.length >= 4 && !(row[0] instanceof Object[]) ? row : (Object[]) row[0];
        return new PurchaseSummaryResponse(
                asLong(values[0]), asLong(values[1]), asLong(values[2]), asMoney(values[3]));
    }

    @Transactional(readOnly = true)
    public PurchaseResponse get(UUID id) {
        return toResponse(require(id), true);
    }

    @Transactional
    public PurchaseResponse create(PurchaseRequest request) {
        Tenant tenant = currentTenant();
        Supplier supplier = supplierService.requireActive(parseUuid(request.supplierId(), "supplierId"));
        Purchase purchase = new Purchase(
                UUID.randomUUID(),
                tenant,
                supplier,
                nextPurchaseNumber(tenant),
                request.purchaseDate(),
                blankToNull(request.notes()),
                TenantContext.require().userId());
        purchase.replaceItems(buildItems(tenant, purchase, request.items(), true));
        return toResponse(purchaseRepository.save(purchase), true);
    }

    @Transactional
    public PurchaseResponse update(UUID id, PurchaseRequest request) {
        Purchase purchase = requireDraft(id);
        Tenant tenant = purchase.getTenant();
        purchase.setSupplier(supplierService.requireActive(parseUuid(request.supplierId(), "supplierId")));
        purchase.setPurchaseDate(request.purchaseDate());
        purchase.setNotes(blankToNull(request.notes()));
        purchase.replaceItems(buildItems(tenant, purchase, request.items(), true));
        return toResponse(purchase, true);
    }

    @Transactional
    public PurchaseResponse receive(UUID id) {
        Purchase purchase = lockDraft(id);
        if (purchase.getItems().isEmpty()) {
            throw new RetailflowException(
                    ErrorCodes.PURCHASE_EMPTY,
                    "Add at least one product before receiving this purchase",
                    HttpStatus.BAD_REQUEST.value());
        }
        purchase.recalculateTotals();
        for (PurchaseItem item : purchase.getItems()) {
            requireProduct(item.getProduct().getId());
        }
        for (PurchaseItem item : purchase.getItems()) {
            inventoryService.applyPurchaseReceipt(item.getProduct().getId(), item.getQuantity(), purchase.getId());
        }
        purchase.markReceived(Instant.now());
        return toResponse(purchase, true);
    }

    @Transactional
    public PurchaseResponse cancel(UUID id) {
        Purchase purchase = lockDraft(id);
        purchase.markCancelled();
        return toResponse(purchase, true);
    }

    private Purchase lockDraft(UUID id) {
        Purchase purchase = purchaseRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.PURCHASE_NOT_FOUND, "Purchase not found", HttpStatus.NOT_FOUND.value()));
        requireDraftStatus(purchase);
        purchase.getItems().size();
        return purchase;
    }

    private Purchase require(UUID id) {
        return purchaseRepository
                .findById(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.PURCHASE_NOT_FOUND, "Purchase not found", HttpStatus.NOT_FOUND.value()));
    }

    private Purchase requireDraft(UUID id) {
        Purchase purchase = require(id);
        requireDraftStatus(purchase);
        return purchase;
    }

    private void requireDraftStatus(Purchase purchase) {
        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new RetailflowException(
                    ErrorCodes.PURCHASE_NOT_DRAFT,
                    purchase.getStatus() == PurchaseStatus.RECEIVED
                            ? "This purchase has already been received and cannot be changed"
                            : "Only draft purchases can be changed",
                    HttpStatus.CONFLICT.value());
        }
    }

    private List<PurchaseItem> buildItems(
            Tenant tenant, Purchase purchase, List<PurchaseItemRequest> requests, boolean requireActiveProduct) {
        List<PurchaseItem> items = new ArrayList<>();
        for (PurchaseItemRequest line : requests) {
            Product product = requireProduct(parseUuid(line.productId(), "productId"));
            if (requireActiveProduct && !product.isActive()) {
                throw new RetailflowException(
                        ErrorCodes.PRODUCT_INACTIVE,
                        "Inactive products cannot be added to a new purchase",
                        HttpStatus.CONFLICT.value());
            }
            GstRate gst = parseGst(line.gstRate());
            BigDecimal quantity = line.quantity().setScale(3, java.math.RoundingMode.UNNECESSARY);
            BigDecimal unitCost = line.unitCost().setScale(2, java.math.RoundingMode.UNNECESSARY);
            PurchaseMoney.LineTotals totals = PurchaseMoney.line(quantity, unitCost, gst.percent());
            items.add(new PurchaseItem(
                    UUID.randomUUID(), tenant, purchase, product, quantity, unitCost, gst.percent(), totals));
        }
        return items;
    }

    private String nextPurchaseNumber(Tenant tenant) {
        PurchaseNumberCounter counter = counterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseGet(() -> createCounter(tenant));
        return "PUR-" + String.format("%06d", counter.nextValue());
    }

    private PurchaseNumberCounter createCounter(Tenant tenant) {
        try {
            counterRepository.saveAndFlush(new PurchaseNumberCounter(tenant));
        } catch (DataIntegrityViolationException ignored) {
            // another transaction inserted the counter
        }
        return counterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.INTERNAL_ERROR,
                        "Unable to allocate a purchase number",
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
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR, "Invalid " + field, HttpStatus.BAD_REQUEST.value());
        }
    }

    private Tenant currentTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.TENANT_NOT_FOUND, "Company not found", HttpStatus.NOT_FOUND.value()));
    }

    private PurchaseResponse toResponse(Purchase purchase, boolean includeItems) {
        String createdByName = userAccountRepository
                .findById(purchase.getCreatedBy())
                .map(UserAccount::getFullName)
                .orElse("Staff");
        List<PurchaseItemResponse> items = includeItems
                ? purchase.getItems().stream().map(this::toItem).toList()
                : List.of();
        int itemCount = includeItems ? items.size() : purchase.getItemCount();
        return new PurchaseResponse(
                purchase.getId().toString(),
                purchase.getPurchaseNumber(),
                purchase.getSupplier().getId().toString(),
                purchase.getSupplier().getName(),
                purchase.getPurchaseDate(),
                purchase.getStatus(),
                purchase.getSubtotal(),
                purchase.getTaxAmount(),
                purchase.getTotalAmount(),
                purchase.getNotes(),
                purchase.getReceivedAt(),
                purchase.getCreatedBy().toString(),
                createdByName,
                purchase.getCreatedAt(),
                purchase.getUpdatedAt(),
                itemCount,
                items);
    }

    private PurchaseItemResponse toItem(PurchaseItem item) {
        return new PurchaseItemResponse(
                item.getId().toString(),
                item.getProduct().getId().toString(),
                item.getProductName(),
                item.getSku(),
                item.getUnit(),
                item.getQuantity(),
                item.getUnitCost(),
                item.getGstRate(),
                item.getLineSubtotal(),
                item.getTaxAmount(),
                item.getLineTotal());
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
