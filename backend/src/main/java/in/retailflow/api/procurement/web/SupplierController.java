package in.retailflow.api.procurement.web;

import in.retailflow.api.catalog.dto.StatusUpdateRequest;
import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.procurement.dto.SupplierRequest;
import in.retailflow.api.procurement.dto.SupplierResponse;
import in.retailflow.api.procurement.dto.SupplierSummaryResponse;
import in.retailflow.api.procurement.service.SupplierService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PageResponse<SupplierResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(supplierService.list(q, active, page, size));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SupplierSummaryResponse> summary() {
        return ApiResponse.ok(supplierService.summary());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<List<SupplierResponse>> active() {
        return ApiResponse.ok(supplierService.listActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SupplierResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(supplierService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        return ApiResponse.ok(supplierService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SupplierResponse> update(@PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
        return ApiResponse.ok(supplierService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SupplierResponse> status(@PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.ok(supplierService.updateStatus(id, request));
    }
}
