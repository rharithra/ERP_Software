package in.retailflow.api.tenant.service;

import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.security.tenant.TenantSessionBinder;
import in.retailflow.api.tenant.domain.Tenant;
import in.retailflow.api.tenant.dto.TenantResponse;
import in.retailflow.api.tenant.dto.UpdateTenantRequest;
import in.retailflow.api.tenant.repository.TenantRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantSessionBinder tenantSessionBinder;

    public TenantService(TenantRepository tenantRepository, TenantSessionBinder tenantSessionBinder) {
        this.tenantRepository = tenantRepository;
        this.tenantSessionBinder = tenantSessionBinder;
    }

    @Transactional(readOnly = true)
    public TenantResponse getCurrent() {
        Tenant tenant = loadCurrentTenant();
        return toResponse(tenant);
    }

    @Transactional
    public TenantResponse updateCurrent(UpdateTenantRequest request) {
        Tenant tenant = loadCurrentTenant();
        tenant.setName(request.name().trim());
        tenant.setLegalName(blankToNull(request.legalName()));
        tenant.setGstin(blankToNull(request.gstin()));
        tenant.setPhone(blankToNull(request.phone()));
        tenant.setEmail(blankToNull(request.email()));
        tenant.setAddressLine1(blankToNull(request.addressLine1()));
        tenant.setAddressLine2(blankToNull(request.addressLine2()));
        tenant.setCity(blankToNull(request.city()));
        tenant.setState(blankToNull(request.state()));
        tenant.setPincode(blankToNull(request.pincode()));
        return toResponse(tenant);
    }

    private Tenant loadCurrentTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        tenantSessionBinder.bindCurrentTenant(tenantId);
        return tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.TENANT_NOT_FOUND, "Company not found", HttpStatus.NOT_FOUND.value()));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId().toString(),
                tenant.getName(),
                tenant.getLegalName(),
                tenant.getGstin(),
                tenant.getPhone(),
                tenant.getEmail(),
                tenant.getAddressLine1(),
                tenant.getAddressLine2(),
                tenant.getCity(),
                tenant.getState(),
                tenant.getPincode(),
                tenant.getCurrency(),
                tenant.getTimezone());
    }
}
