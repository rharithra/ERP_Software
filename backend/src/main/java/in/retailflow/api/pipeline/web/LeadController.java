package in.retailflow.api.pipeline.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.pipeline.domain.LeadStatus;
import in.retailflow.api.pipeline.dto.LeadRequest;
import in.retailflow.api.pipeline.dto.LeadResponse;
import in.retailflow.api.pipeline.dto.LeadStatusRequest;
import in.retailflow.api.pipeline.dto.PipelineActivityResponse;
import in.retailflow.api.pipeline.service.LeadService;
import in.retailflow.api.sales.dto.CustomerResponse;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<PageResponse<LeadResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(leadService.list(q, status, priority, page, size));
    }

    @GetMapping("/board")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<List<LeadResponse>> board() {
        return ApiResponse.ok(leadService.board());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<LeadResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(leadService.get(id));
    }

    @GetMapping("/{id}/activities")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<List<PipelineActivityResponse>> timeline(@PathVariable UUID id) {
        return ApiResponse.ok(leadService.timeline(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<LeadResponse> create(@Valid @RequestBody LeadRequest request) {
        return ApiResponse.ok(leadService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<LeadResponse> update(@PathVariable UUID id, @Valid @RequestBody LeadRequest request) {
        return ApiResponse.ok(leadService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<LeadResponse> status(@PathVariable UUID id, @Valid @RequestBody LeadStatusRequest request) {
        return ApiResponse.ok(leadService.changeStatus(id, request));
    }

    @PostMapping("/{id}/convert-customer")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<CustomerResponse> convert(@PathVariable UUID id) {
        return ApiResponse.ok(leadService.convertToCustomer(id));
    }
}
