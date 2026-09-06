package in.retailflow.api.sales.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.sales.domain.SaleReturnStatus;
import in.retailflow.api.sales.dto.SaleReturnRequest;
import in.retailflow.api.sales.dto.SaleReturnResponse;
import in.retailflow.api.sales.service.SaleReturnService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/returns")
public class SaleReturnController {

    private final SaleReturnService saleReturnService;

    public SaleReturnController(SaleReturnService saleReturnService) {
        this.saleReturnService = saleReturnService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PageResponse<SaleReturnResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID saleId,
            @RequestParam(required = false) SaleReturnStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(saleReturnService.list(q, customerId, saleId, status, fromDate, toDate, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleReturnResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(saleReturnService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleReturnResponse> create(@Valid @RequestBody SaleReturnRequest request) {
        return ApiResponse.ok(saleReturnService.create(request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleReturnResponse> complete(@PathVariable UUID id) {
        return ApiResponse.ok(saleReturnService.complete(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<SaleReturnResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(saleReturnService.cancel(id));
    }
}
