package in.retailflow.api.sales.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.sales.domain.RefundStatus;
import in.retailflow.api.sales.dto.RefundRequest;
import in.retailflow.api.sales.dto.RefundResponse;
import in.retailflow.api.sales.service.RefundService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<List<RefundResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RefundStatus status,
            @RequestParam(required = false) UUID saleId,
            @RequestParam(required = false) UUID customerId) {
        return ApiResponse.ok(refundService.list(q, status, saleId, customerId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<RefundResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(refundService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<RefundResponse> create(@Valid @RequestBody RefundRequest request) {
        return ApiResponse.ok(refundService.create(request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<RefundResponse> complete(@PathVariable UUID id) {
        return ApiResponse.ok(refundService.complete(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<RefundResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(refundService.cancel(id));
    }
}
