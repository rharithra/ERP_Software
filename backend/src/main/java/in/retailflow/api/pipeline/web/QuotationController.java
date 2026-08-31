package in.retailflow.api.pipeline.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.pipeline.domain.QuotationStatus;
import in.retailflow.api.pipeline.dto.QuotationRequest;
import in.retailflow.api.pipeline.dto.QuotationResponse;
import in.retailflow.api.pipeline.service.QuotationService;
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
@RequestMapping("/api/v1/quotations")
public class QuotationController {

    private final QuotationService quotationService;

    public QuotationController(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<PageResponse<QuotationResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) QuotationStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(quotationService.list(q, status, customerId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<QuotationResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(quotationService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<QuotationResponse> create(@Valid @RequestBody QuotationRequest request) {
        return ApiResponse.ok(quotationService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<QuotationResponse> update(@PathVariable UUID id, @Valid @RequestBody QuotationRequest request) {
        return ApiResponse.ok(quotationService.update(id, request));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<QuotationResponse> send(@PathVariable UUID id) {
        return ApiResponse.ok(quotationService.markSent(id));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<QuotationResponse> accept(@PathVariable UUID id) {
        return ApiResponse.ok(quotationService.accept(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<QuotationResponse> reject(@PathVariable UUID id) {
        return ApiResponse.ok(quotationService.reject(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<QuotationResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(quotationService.cancel(id));
    }
}
