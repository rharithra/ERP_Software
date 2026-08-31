package in.retailflow.api.pipeline.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.pipeline.domain.FollowUpStatus;
import in.retailflow.api.pipeline.dto.FollowUpOutcomeRequest;
import in.retailflow.api.pipeline.dto.FollowUpRequest;
import in.retailflow.api.pipeline.dto.FollowUpResponse;
import in.retailflow.api.pipeline.service.FollowUpService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/api/v1/follow-ups")
public class FollowUpController {

    private final FollowUpService followUpService;

    public FollowUpController(FollowUpService followUpService) {
        this.followUpService = followUpService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<List<FollowUpResponse>> list(
            @RequestParam(required = false) FollowUpStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID leadId) {
        if (leadId != null) {
            return ApiResponse.ok(followUpService.forLead(leadId));
        }
        return ApiResponse.ok(followUpService.list(status, fromDate, toDate));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<FollowUpResponse> create(@Valid @RequestBody FollowUpRequest request) {
        return ApiResponse.ok(followUpService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<FollowUpResponse> update(@PathVariable UUID id, @Valid @RequestBody FollowUpRequest request) {
        return ApiResponse.ok(followUpService.update(id, request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<FollowUpResponse> complete(
            @PathVariable UUID id, @RequestBody(required = false) FollowUpOutcomeRequest request) {
        return ApiResponse.ok(followUpService.complete(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<FollowUpResponse> cancel(
            @PathVariable UUID id, @RequestBody(required = false) FollowUpOutcomeRequest request) {
        return ApiResponse.ok(followUpService.cancel(id, request));
    }
}
