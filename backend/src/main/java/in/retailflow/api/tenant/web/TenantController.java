package in.retailflow.api.tenant.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.tenant.dto.TenantResponse;
import in.retailflow.api.tenant.dto.UpdateTenantRequest;
import in.retailflow.api.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<TenantResponse> getTenant() {
        return ApiResponse.ok(tenantService.getCurrent());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<TenantResponse> updateTenant(@Valid @RequestBody UpdateTenantRequest request) {
        return ApiResponse.ok(tenantService.updateCurrent(request));
    }
}
