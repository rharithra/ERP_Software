package in.retailflow.api.tenant.service;

import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.identity.domain.TenantRole;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.BusinessType;
import in.retailflow.api.tenant.domain.SalesMode;
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

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
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
        applySalesConfiguration(tenant, request.businessType(), request.salesMode());
        return toResponse(tenant);
    }

    private void applySalesConfiguration(Tenant tenant, String businessTypeValue, String salesModeValue) {
        BusinessType nextType = parseBusinessType(businessTypeValue, true);
        SalesMode nextMode = parseSalesMode(salesModeValue, true);
        if (nextType == null && nextMode == null) {
            return;
        }
        BusinessType resolvedType = nextType == null ? tenant.getBusinessType() : nextType;
        SalesMode resolvedMode = nextMode == null ? tenant.getSalesMode() : nextMode;
        boolean changed = resolvedType != tenant.getBusinessType() || resolvedMode != tenant.getSalesMode();
        if (changed && !isOwner()) {
            throw new RetailflowException(
                    ErrorCodes.ACCESS_DENIED,
                    "Only the owner can change business type or sales experience",
                    HttpStatus.FORBIDDEN.value());
        }
        tenant.setBusinessType(resolvedType);
        tenant.setSalesMode(resolvedMode);
    }

    private static boolean isOwner() {
        return TenantRole.OWNER.name().equals(TenantContext.require().role());
    }

    public static BusinessType parseBusinessType(String value, boolean optional) {
        if (value == null || value.isBlank()) {
            if (optional) {
                return null;
            }
            throw invalid("Business type is required");
        }
        try {
            return BusinessType.fromValue(value);
        } catch (IllegalArgumentException ex) {
            throw invalid("Business type must be a supported RetailFlow business type");
        }
    }

    public static SalesMode parseSalesMode(String value, boolean optional) {
        if (value == null || value.isBlank()) {
            if (optional) {
                return null;
            }
            throw invalid("Sales mode is required");
        }
        try {
            return SalesMode.fromValue(value);
        } catch (IllegalArgumentException ex) {
            throw invalid("Sales mode must be QUICK_SALE, PIPELINE, or HYBRID");
        }
    }

    private static RetailflowException invalid(String message) {
        return new RetailflowException(ErrorCodes.VALIDATION_ERROR, message, HttpStatus.BAD_REQUEST.value());
    }

    private Tenant loadCurrentTenant() {
        UUID tenantId = TenantContext.requireTenantId();
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

    public static TenantResponse toResponse(Tenant tenant) {
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
                tenant.getTimezone(),
                tenant.getBusinessType(),
                tenant.getSalesMode());
    }
}
