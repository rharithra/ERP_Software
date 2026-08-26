package in.retailflow.api.inventory.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.inventory.domain.InventoryStatus;
import in.retailflow.api.inventory.dto.InventoryItemResponse;
import in.retailflow.api.inventory.dto.InventorySummaryResponse;
import in.retailflow.api.inventory.dto.OpeningStockRequest;
import in.retailflow.api.inventory.dto.ReorderLevelRequest;
import in.retailflow.api.inventory.dto.StockAdjustmentRequest;
import in.retailflow.api.inventory.dto.StockMovementResponse;
import in.retailflow.api.inventory.service.InventoryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PageResponse<InventoryItemResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) InventoryStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inventoryService.list(q, categoryId, status, page, size));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<InventorySummaryResponse> summary() {
        return ApiResponse.ok(inventoryService.summary());
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<InventoryItemResponse> get(@PathVariable UUID productId) {
        return ApiResponse.ok(inventoryService.get(productId));
    }

    @GetMapping("/{productId}/movements")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PageResponse<StockMovementResponse>> movements(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inventoryService.movements(productId, page, size));
    }

    @PostMapping("/{productId}/opening-stock")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<InventoryItemResponse> openingStock(
            @PathVariable UUID productId, @Valid @RequestBody OpeningStockRequest request) {
        return ApiResponse.ok(inventoryService.recordOpeningStock(productId, request));
    }

    @PostMapping("/{productId}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<InventoryItemResponse> adjust(
            @PathVariable UUID productId, @Valid @RequestBody StockAdjustmentRequest request) {
        return ApiResponse.ok(inventoryService.adjust(productId, request));
    }

    @PatchMapping("/{productId}/reorder-level")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<InventoryItemResponse> reorderLevel(
            @PathVariable UUID productId, @Valid @RequestBody ReorderLevelRequest request) {
        return ApiResponse.ok(inventoryService.updateReorderLevel(productId, request));
    }
}
