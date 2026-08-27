package in.retailflow.api.procurement.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.procurement.domain.PurchaseStatus;
import in.retailflow.api.procurement.dto.PurchaseRequest;
import in.retailflow.api.procurement.dto.PurchaseResponse;
import in.retailflow.api.procurement.dto.PurchaseSummaryResponse;
import in.retailflow.api.procurement.service.PurchaseService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PageResponse<PurchaseResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(purchaseService.list(q, supplierId, status, fromDate, toDate, page, size));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PurchaseSummaryResponse> summary() {
        return ApiResponse.ok(purchaseService.summary());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PurchaseResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(purchaseService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<PurchaseResponse> create(@Valid @RequestBody PurchaseRequest request) {
        return ApiResponse.ok(purchaseService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<PurchaseResponse> update(@PathVariable UUID id, @Valid @RequestBody PurchaseRequest request) {
        return ApiResponse.ok(purchaseService.update(id, request));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<PurchaseResponse> receive(@PathVariable UUID id) {
        return ApiResponse.ok(purchaseService.receive(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<PurchaseResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(purchaseService.cancel(id));
    }
}
