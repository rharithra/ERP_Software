package in.retailflow.api.sales.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.sales.domain.SaleStatus;
import in.retailflow.api.sales.dto.CompleteSaleRequest;
import in.retailflow.api.sales.dto.SaleDashboardResponse;
import in.retailflow.api.sales.dto.SaleInvoiceResponse;
import in.retailflow.api.sales.dto.SaleRequest;
import in.retailflow.api.sales.dto.SaleResponse;
import in.retailflow.api.sales.dto.SaleSummaryResponse;
import in.retailflow.api.sales.service.SaleService;
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
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PageResponse<SaleResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(saleService.list(q, customerId, status, fromDate, toDate, page, size));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleSummaryResponse> summary() {
        return ApiResponse.ok(saleService.summary());
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleDashboardResponse> dashboard() {
        return ApiResponse.ok(saleService.dashboard());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(saleService.get(id));
    }

    @GetMapping("/{id}/invoice")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleInvoiceResponse> invoice(@PathVariable UUID id) {
        return ApiResponse.ok(saleService.invoice(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleResponse> create(@Valid @RequestBody SaleRequest request) {
        return ApiResponse.ok(saleService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleResponse> update(@PathVariable UUID id, @Valid @RequestBody SaleRequest request) {
        return ApiResponse.ok(saleService.update(id, request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleResponse> complete(
            @PathVariable UUID id, @Valid @RequestBody CompleteSaleRequest request) {
        return ApiResponse.ok(saleService.complete(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(saleService.cancel(id));
    }
}
