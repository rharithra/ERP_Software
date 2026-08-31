package in.retailflow.api.pipeline.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.pipeline.domain.SalesOrderStatus;
import in.retailflow.api.pipeline.dto.SalesOrderRequest;
import in.retailflow.api.pipeline.dto.SalesOrderResponse;
import in.retailflow.api.pipeline.service.SalesOrderService;
import in.retailflow.api.sales.dto.SaleResponse;
import jakarta.validation.Valid;
import java.util.UUID;
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
@RequestMapping("/api/v1/sales-orders")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<PageResponse<SalesOrderResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(salesOrderService.list(q, status, customerId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SalesOrderResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(salesOrderService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SalesOrderResponse> create(@Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.ok(salesOrderService.create(request));
    }

    @PostMapping("/from-quotation/{quotationId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SalesOrderResponse> fromQuotation(@PathVariable UUID quotationId) {
        return ApiResponse.ok(salesOrderService.createFromQuotation(quotationId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SalesOrderResponse> update(@PathVariable UUID id, @Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.ok(salesOrderService.update(id, request));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SalesOrderResponse> confirm(@PathVariable UUID id) {
        return ApiResponse.ok(salesOrderService.confirm(id));
    }

    @PostMapping("/{id}/process")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SalesOrderResponse> process(@PathVariable UUID id) {
        return ApiResponse.ok(salesOrderService.process(id));
    }

    @PostMapping("/{id}/ready")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SalesOrderResponse> ready(@PathVariable UUID id) {
        return ApiResponse.ok(salesOrderService.ready(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SalesOrderResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(salesOrderService.cancel(id));
    }

    @PostMapping("/{id}/convert-sale")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<SaleResponse> convert(@PathVariable UUID id) {
        return ApiResponse.ok(salesOrderService.convertToSale(id));
    }
}
