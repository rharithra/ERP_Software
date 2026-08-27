package in.retailflow.api.inventory.service;

import in.retailflow.api.catalog.domain.Product;
import in.retailflow.api.catalog.repository.ProductRepository;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.identity.domain.UserAccount;
import in.retailflow.api.identity.repository.UserAccountRepository;
import in.retailflow.api.inventory.domain.AdjustmentReason;
import in.retailflow.api.inventory.domain.InventoryBalance;
import in.retailflow.api.inventory.domain.InventoryStatus;
import in.retailflow.api.inventory.domain.InventoryStatuses;
import in.retailflow.api.inventory.domain.StockMovement;
import in.retailflow.api.inventory.domain.StockMovementType;
import in.retailflow.api.inventory.dto.InventoryItemResponse;
import in.retailflow.api.inventory.dto.InventorySummaryResponse;
import in.retailflow.api.inventory.dto.OpeningStockRequest;
import in.retailflow.api.inventory.dto.ReorderLevelRequest;
import in.retailflow.api.inventory.dto.StockAdjustmentRequest;
import in.retailflow.api.inventory.dto.StockMovementResponse;
import in.retailflow.api.inventory.repository.InventoryBalanceRepository;
import in.retailflow.api.inventory.repository.InventoryProductQueryRepository;
import in.retailflow.api.inventory.repository.StockMovementRepository;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import in.retailflow.api.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3, RoundingMode.UNNECESSARY);

    private final InventoryBalanceRepository balanceRepository;
    private final InventoryProductQueryRepository productQueryRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;

    public InventoryService(
            InventoryBalanceRepository balanceRepository,
            InventoryProductQueryRepository productQueryRepository,
            ProductRepository productRepository,
            StockMovementRepository movementRepository,
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository) {
        this.balanceRepository = balanceRepository;
        this.productQueryRepository = productQueryRepository;
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> list(String query, UUID categoryId, InventoryStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), clampSize(size), Sort.by("name").ascending());
        var products = productQueryRepository.search(
                blankToNull(query), categoryId, status == null ? null : status.name(), pageable);
        List<UUID> ids = products.getContent().stream().map(Product::getId).toList();
        Map<UUID, InventoryBalance> balances = ids.isEmpty()
                ? Map.of()
                : balanceRepository.findByProduct_IdIn(ids).stream()
                        .collect(Collectors.toMap(b -> b.getProduct().getId(), Function.identity()));
        return PageResponse.from(products.map(product -> toItem(product, balances.get(product.getId()))));
    }

    @Transactional(readOnly = true)
    public InventorySummaryResponse summary() {
        Object[] row = productQueryRepository.summarize();
        Object[] values = row.length == 4 && !(row[0] instanceof Object[]) ? row : (Object[]) row[0];
        return new InventorySummaryResponse(asLong(values[0]), asLong(values[1]), asLong(values[2]), asLong(values[3]));
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse get(UUID productId) {
        Product product = requireProduct(productId);
        InventoryBalance balance = balanceRepository.findByProduct_Id(productId).orElse(null);
        return toItem(product, balance);
    }

    @Transactional
    public InventoryItemResponse recordOpeningStock(UUID productId, OpeningStockRequest request) {
        Product product = requireProduct(productId);
        InventoryBalance balance = lockOrCreate(product);
        if (balance.isOpeningRecorded() || movementRepository.existsByProduct_Id(productId)) {
            throw new RetailflowException(
                    ErrorCodes.OPENING_STOCK_ALREADY_RECORDED,
                    "Opening stock has already been recorded for this product. Use a stock adjustment instead.",
                    HttpStatus.CONFLICT.value());
        }
        BigDecimal quantity = scale(request.quantity());
        BigDecimal before = scale(balance.getQuantity());
        if (before.compareTo(ZERO) != 0) {
            throw new RetailflowException(
                    ErrorCodes.OPENING_STOCK_ALREADY_RECORDED,
                    "This product already has stock. Use a stock adjustment instead.",
                    HttpStatus.CONFLICT.value());
        }
        apply(
                balance,
                product,
                StockMovementType.OPENING_STOCK,
                quantity,
                before,
                quantity,
                null,
                blankToNull(request.notes()),
                null,
                null);
        balance.setOpeningRecorded(true);
        return toItem(product, balance);
    }

    @Transactional
    public InventoryItemResponse adjust(UUID productId, StockAdjustmentRequest request) {
        if (request.type() != StockMovementType.ADJUSTMENT_IN && request.type() != StockMovementType.ADJUSTMENT_OUT) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR,
                    "Adjustment type must be ADJUSTMENT_IN or ADJUSTMENT_OUT",
                    HttpStatus.BAD_REQUEST.value());
        }
        Product product = requireProduct(productId);
        InventoryBalance balance = lockOrCreate(product);
        BigDecimal change = scale(request.quantity());
        BigDecimal before = scale(balance.getQuantity());
        BigDecimal after;
        if (request.type() == StockMovementType.ADJUSTMENT_IN) {
            after = before.add(change);
        } else {
            after = before.subtract(change);
            if (after.compareTo(ZERO) < 0) {
                throw new RetailflowException(
                        ErrorCodes.INSUFFICIENT_STOCK,
                        "Insufficient stock. Available quantity: " + strip(before),
                        HttpStatus.CONFLICT.value());
            }
        }
        apply(
                balance,
                product,
                request.type(),
                change,
                before,
                after,
                request.reason(),
                blankToNull(request.notes()),
                null,
                null);
        return toItem(product, balance);
    }

    /**
     * Increases stock for a received purchase. Must run inside the purchase receive
     * transaction so a failed line rolls back every line.
     */
    @Transactional
    public InventoryItemResponse applyPurchaseReceipt(UUID productId, BigDecimal quantity, UUID purchaseId) {
        Product product = requireProduct(productId);
        InventoryBalance balance = lockOrCreate(product);
        BigDecimal change = scale(quantity);
        if (change.compareTo(ZERO) <= 0) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR,
                    "Purchase receipt quantity must be greater than zero",
                    HttpStatus.BAD_REQUEST.value());
        }
        BigDecimal before = scale(balance.getQuantity());
        BigDecimal after = before.add(change);
        apply(
                balance,
                product,
                StockMovementType.PURCHASE_RECEIPT,
                change,
                before,
                after,
                null,
                "Purchase received",
                "PURCHASE",
                purchaseId);
        return toItem(product, balance);
    }

    @Transactional
    public InventoryItemResponse updateReorderLevel(UUID productId, ReorderLevelRequest request) {
        Product product = requireProduct(productId);
        InventoryBalance balance = lockOrCreate(product);
        balance.setReorderLevel(scale(request.reorderLevel()));
        return toItem(product, balance);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> movements(UUID productId, int page, int size) {
        requireProduct(productId);
        var pageable = PageRequest.of(Math.max(page - 1, 0), clampSize(size));
        var result = movementRepository.findByProduct_IdOrderByCreatedAtDesc(productId, pageable);
        Map<UUID, String> names = userAccountRepository
                .findAllById(result.getContent().stream().map(StockMovement::getCreatedBy).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName));
        return PageResponse.from(result.map(movement -> toMovement(movement, names.get(movement.getCreatedBy()))));
    }

    private void apply(
            InventoryBalance balance,
            Product product,
            StockMovementType type,
            BigDecimal quantity,
            BigDecimal before,
            BigDecimal after,
            AdjustmentReason reason,
            String notes,
            String referenceType,
            UUID referenceId) {
        balance.setQuantity(after);
        movementRepository.save(new StockMovement(
                UUID.randomUUID(),
                balance.getTenant(),
                product,
                type,
                quantity,
                before,
                after,
                reason,
                notes,
                TenantContext.require().userId(),
                referenceType,
                referenceId));
    }

    private InventoryBalance lockOrCreate(Product product) {
        return balanceRepository
                .findByProductIdForUpdate(product.getId())
                .orElseGet(() -> createBalance(product));
    }

    private InventoryBalance createBalance(Product product) {
        Tenant tenant = currentTenant();
        try {
            InventoryBalance created = balanceRepository.saveAndFlush(
                    new InventoryBalance(UUID.randomUUID(), tenant, product));
            return balanceRepository.findByProductIdForUpdate(product.getId()).orElse(created);
        } catch (DataIntegrityViolationException ex) {
            return balanceRepository
                    .findByProductIdForUpdate(product.getId())
                    .orElseThrow(() -> new RetailflowException(
                            ErrorCodes.INTERNAL_ERROR,
                            "Unable to lock inventory",
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    private Product requireProduct(UUID productId) {
        return productRepository
                .findById(productId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.PRODUCT_NOT_FOUND, "Product not found", HttpStatus.NOT_FOUND.value()));
    }

    private Tenant currentTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.TENANT_NOT_FOUND, "Company not found", HttpStatus.NOT_FOUND.value()));
    }

    private InventoryItemResponse toItem(Product product, InventoryBalance balance) {
        BigDecimal quantity = balance == null ? ZERO : scale(balance.getQuantity());
        BigDecimal reorder = balance == null ? ZERO : scale(balance.getReorderLevel());
        return new InventoryItemResponse(
                product.getId().toString(),
                product.getName(),
                product.getSku(),
                product.getCategory().getId().toString(),
                product.getCategory().getName(),
                product.getUnit().name(),
                quantity,
                reorder,
                InventoryStatuses.from(quantity, reorder),
                balance != null && balance.isOpeningRecorded(),
                balance == null ? product.getUpdatedAt() : balance.getUpdatedAt());
    }

    private StockMovementResponse toMovement(StockMovement movement, String createdByName) {
        return new StockMovementResponse(
                movement.getId().toString(),
                movement.getProduct().getId().toString(),
                movement.getMovementType(),
                movement.getQuantity(),
                movement.getQuantityBefore(),
                movement.getQuantityAfter(),
                movement.getReferenceType(),
                movement.getReferenceId() == null ? null : movement.getReferenceId().toString(),
                movement.getReason(),
                movement.getNotes(),
                movement.getCreatedBy().toString(),
                createdByName == null ? "Staff" : createdByName,
                movement.getCreatedAt());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(3, RoundingMode.UNNECESSARY);
    }

    private static String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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
}
