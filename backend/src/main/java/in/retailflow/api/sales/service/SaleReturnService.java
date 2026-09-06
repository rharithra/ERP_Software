package in.retailflow.api.sales.service;

import in.retailflow.api.common.DocumentNumbers;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.identity.domain.UserAccount;
import in.retailflow.api.identity.repository.UserAccountRepository;
import in.retailflow.api.inventory.service.InventoryService;
import in.retailflow.api.sales.domain.ReturnNumberCounter;
import in.retailflow.api.sales.domain.Sale;
import in.retailflow.api.sales.domain.SaleItem;
import in.retailflow.api.sales.domain.SaleMoney;
import in.retailflow.api.sales.domain.SaleReturn;
import in.retailflow.api.sales.domain.SaleReturnItem;
import in.retailflow.api.sales.domain.SaleReturnStatus;
import in.retailflow.api.sales.domain.SaleStatus;
import in.retailflow.api.sales.dto.SaleReturnItemResponse;
import in.retailflow.api.sales.dto.SaleReturnRequest;
import in.retailflow.api.sales.dto.SaleReturnResponse;
import in.retailflow.api.sales.repository.ReturnNumberCounterRepository;
import in.retailflow.api.sales.repository.SaleRepository;
import in.retailflow.api.sales.repository.SaleReturnRepository;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleReturnService {

    private final SaleReturnRepository returnRepository;
    private final ReturnNumberCounterRepository counterRepository;
    private final SaleRepository saleRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final CustomerCreditService creditService;
    private final UserAccountRepository userAccountRepository;

    public SaleReturnService(
            SaleReturnRepository returnRepository,
            ReturnNumberCounterRepository counterRepository,
            SaleRepository saleRepository,
            InventoryService inventoryService,
            PaymentService paymentService,
            CustomerCreditService creditService,
            UserAccountRepository userAccountRepository) {
        this.returnRepository = returnRepository;
        this.counterRepository = counterRepository;
        this.saleRepository = saleRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.creditService = creditService;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleReturnResponse> list(
            String q,
            UUID customerId,
            UUID saleId,
            SaleReturnStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100), Sort.by("returnDate").descending());
        return PageResponse.from(returnRepository
                .search(q, customerId, saleId, status == null ? null : status.name(), fromDate, toDate, pageable)
                .map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public SaleReturnResponse get(UUID id) {
        return toResponse(require(id), true);
    }

    @Transactional(readOnly = true)
    public List<SaleReturnResponse> forSale(UUID saleId) {
        return returnRepository.findBySale_IdOrderByCreatedAtDesc(saleId).stream()
                .map(item -> toResponse(item, false))
                .toList();
    }

    @Transactional
    public SaleReturnResponse create(SaleReturnRequest request) {
        Sale sale = lockSale(request.saleId());
        requireReturnable(sale);
        SaleReturn saleReturn = new SaleReturn(
                UUID.randomUUID(),
                sale.getTenant(),
                nextNumber(sale.getTenant()),
                sale,
                request.returnDate() == null ? LocalDate.now() : request.returnDate(),
                TenantContext.require().userId());
        saleReturn.setReason(request.reason());
        saleReturn.setNotes(blank(request.notes()));
        applyItems(sale, saleReturn, request.items());
        return toResponse(returnRepository.save(saleReturn), true);
    }

    @Transactional
    public SaleReturnResponse complete(UUID id) {
        SaleReturn saleReturn = returnRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.RETURN_NOT_FOUND, "Return not found", HttpStatus.NOT_FOUND.value()));
        Sale sale = lockSale(saleReturn.getSale().getId());
        saleReturn.requireDraft();
        saleReturn.getItems().size();
        if (saleReturn.getItems().isEmpty()) {
            throw new RetailflowException(
                    ErrorCodes.RETURN_HAS_NO_ITEMS, "Add at least one item before completing this return", HttpStatus.BAD_REQUEST.value());
        }
        validateQuantities(sale, saleReturn.getItems(), null);
        creditService.lockCustomer(sale);
        List<SaleReturnItem> ordered = saleReturn.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        for (SaleReturnItem item : ordered) {
            inventoryService.applySaleReturn(item.getProduct().getId(), item.getQuantity(), saleReturn.getId());
        }
        saleReturn.markCompleted(TenantContext.require().userId(), Instant.now());
        paymentService.applySaleTotals(sale);
        creditService.syncCreditCreated(sale, saleReturn);
        paymentService.applySaleTotals(sale);
        return toResponse(saleReturn, true);
    }

    @Transactional
    public SaleReturnResponse cancel(UUID id) {
        SaleReturn saleReturn = returnRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.RETURN_NOT_FOUND, "Return not found", HttpStatus.NOT_FOUND.value()));
        saleReturn.markCancelled();
        return toResponse(saleReturn, true);
    }

    public BigDecimal availableQuantity(UUID saleItemId) {
        SaleItem item = findSaleItem(saleItemId);
        return item.getQuantity().subtract(returnRepository.sumCompletedQuantityForSaleItem(saleItemId));
    }

    private void applyItems(Sale sale, SaleReturn saleReturn, List<SaleReturnRequest.Item> requested) {
        Map<UUID, SaleItem> byId = sale.getItems().stream().collect(Collectors.toMap(SaleItem::getId, item -> item));
        List<SaleReturnItem> built = new ArrayList<>();
        BigDecimal originalLines = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (SaleItem item : sale.getItems()) {
            originalLines = originalLines.add(item.getLineTotal());
        }
        BigDecimal returnLines = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (SaleReturnRequest.Item line : requested) {
            SaleItem origin = byId.get(line.saleItemId());
            if (origin == null) {
                throw new RetailflowException(
                        ErrorCodes.VALIDATION_ERROR, "Return item does not belong to this sale", HttpStatus.BAD_REQUEST.value());
            }
            BigDecimal qty = line.quantity().setScale(3, RoundingMode.HALF_UP);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RetailflowException(
                        ErrorCodes.VALIDATION_ERROR, "Return quantity must be greater than zero", HttpStatus.BAD_REQUEST.value());
            }
            BigDecimal ratio = qty.divide(origin.getQuantity(), 8, RoundingMode.HALF_UP);
            BigDecimal discount = origin.getDiscount().multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            SaleMoney.LineTotals totals = SaleMoney.line(qty, origin.getUnitPrice(), origin.getGstRate(), discount);
            built.add(new SaleReturnItem(UUID.randomUUID(), sale.getTenant(), origin, qty, discount, totals, line.reason()));
            returnLines = returnLines.add(totals.total());
        }
        validateQuantities(sale, built, null);
        BigDecimal header = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (originalLines.compareTo(BigDecimal.ZERO) > 0 && sale.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
            header = sale.getDiscount().multiply(returnLines).divide(originalLines, 2, RoundingMode.HALF_UP);
        }
        saleReturn.replaceItems(built, header);
    }

    private void validateQuantities(Sale sale, List<SaleReturnItem> items, UUID ignoreReturnId) {
        Map<UUID, BigDecimal> requested = items.stream()
                .collect(Collectors.toMap(
                        item -> item.getSaleItem().getId(),
                        SaleReturnItem::getQuantity,
                        BigDecimal::add));
        for (Map.Entry<UUID, BigDecimal> entry : requested.entrySet()) {
            SaleItem origin = sale.getItems().stream()
                    .filter(item -> item.getId().equals(entry.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new RetailflowException(
                            ErrorCodes.VALIDATION_ERROR, "Return item does not belong to this sale", HttpStatus.BAD_REQUEST.value()));
            BigDecimal already = returnRepository.sumCompletedQuantityForSaleItem(origin.getId());
            BigDecimal remaining = origin.getQuantity().subtract(already);
            if (entry.getValue().compareTo(remaining) > 0) {
                throw new RetailflowException(
                        ErrorCodes.RETURN_QUANTITY_EXCEEDS_AVAILABLE,
                        "Only " + remaining.stripTrailingZeros().toPlainString() + " of " + origin.getProductName()
                                + " can still be returned",
                        HttpStatus.CONFLICT.value());
            }
        }
    }

    private void requireReturnable(Sale sale) {
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new RetailflowException(
                    ErrorCodes.SALE_NOT_RETURNABLE, "Only completed sales can be returned", HttpStatus.CONFLICT.value());
        }
    }

    private Sale lockSale(UUID saleId) {
        return saleRepository
                .findByIdForUpdate(saleId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.SALE_NOT_FOUND, "Sale not found", HttpStatus.NOT_FOUND.value()));
    }

    private SaleItem findSaleItem(UUID saleItemId) {
        return saleRepository.findAll().stream()
                .flatMap(sale -> sale.getItems().stream())
                .filter(item -> item.getId().equals(saleItemId))
                .findFirst()
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.NOT_FOUND, "Sale item not found", HttpStatus.NOT_FOUND.value()));
    }

    private String nextNumber(Tenant tenant) {
        ReturnNumberCounter counter = counterRepository
                .findByTenantIdForUpdate(tenant.getId())
                .orElseGet(() -> {
                    try {
                        counterRepository.saveAndFlush(new ReturnNumberCounter(tenant));
                    } catch (DataIntegrityViolationException ignored) {
                    }
                    return counterRepository
                            .findByTenantIdForUpdate(tenant.getId())
                            .orElseThrow(() -> new RetailflowException(
                                    ErrorCodes.INTERNAL_ERROR,
                                    "Unable to allocate a return number",
                                    HttpStatus.INTERNAL_SERVER_ERROR.value()));
                });
        return DocumentNumbers.format("RET-", counter.nextValue());
    }

    private SaleReturn require(UUID id) {
        return returnRepository
                .findById(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.RETURN_NOT_FOUND, "Return not found", HttpStatus.NOT_FOUND.value()));
    }

    private SaleReturnResponse toSummary(SaleReturn saleReturn) {
        return toResponse(saleReturn, false);
    }

    private SaleReturnResponse toResponse(SaleReturn saleReturn, boolean includeItems) {
        String createdByName = userAccountRepository
                .findById(saleReturn.getCreatedBy())
                .map(UserAccount::getFullName)
                .orElse("Staff");
        Sale sale = saleReturn.getSale();
        List<SaleReturnItemResponse> items = includeItems
                ? saleReturn.getItems().stream().map(this::toItem).toList()
                : List.of();
        return new SaleReturnResponse(
                saleReturn.getId().toString(),
                saleReturn.getReturnNumber(),
                sale.getId().toString(),
                sale.getSaleNumber(),
                sale.getInvoiceNumber(),
                saleReturn.getCustomer() == null ? null : saleReturn.getCustomer().getId().toString(),
                sale.getCustomerName(),
                saleReturn.getStatus(),
                saleReturn.getReturnDate(),
                saleReturn.getReason(),
                saleReturn.getNotes(),
                saleReturn.getSubtotal(),
                saleReturn.getDiscount(),
                saleReturn.getTaxAmount(),
                saleReturn.getTotalAmount(),
                saleReturn.getCreatedBy().toString(),
                createdByName,
                saleReturn.getCreatedAt(),
                saleReturn.getCompletedAt(),
                items);
    }

    private SaleReturnItemResponse toItem(SaleReturnItem item) {
        return new SaleReturnItemResponse(
                item.getId().toString(),
                item.getSaleItem().getId().toString(),
                item.getProduct().getId().toString(),
                item.getProductName(),
                item.getSku(),
                item.getBarcode(),
                item.getUnit(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getGstRate(),
                item.getDiscount(),
                item.getTaxAmount(),
                item.getTotalAmount(),
                item.getReason());
    }

    private static String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
