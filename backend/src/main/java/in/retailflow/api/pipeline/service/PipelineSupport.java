package in.retailflow.api.pipeline.service;

import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import in.retailflow.api.tenant.domain.TenantMembership;
import in.retailflow.api.tenant.repository.TenantMembershipRepository;
import in.retailflow.api.tenant.repository.TenantRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PipelineSupport {

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;

    public PipelineSupport(TenantRepository tenantRepository, TenantMembershipRepository membershipRepository) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
    }

    public Tenant currentTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.TENANT_NOT_FOUND, "Company not found", HttpStatus.NOT_FOUND.value()));
    }

    public UUID requireAssigned(String assignedTo) {
        String value = blankToNull(assignedTo);
        if (value == null) {
            return null;
        }
        UUID userId = parseUuid(value, "assignedTo");
        membershipRepository
                .findByUserIdAndTenantId(userId, TenantContext.requireTenantId())
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.VALIDATION_ERROR, "Assigned user is not a member of this company", HttpStatus.BAD_REQUEST.value()));
        return userId;
    }

    public static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new RetailflowException(ErrorCodes.VALIDATION_ERROR, "Invalid " + field, HttpStatus.BAD_REQUEST.value());
        }
    }

    public static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static int clampSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    public static RetailflowException notFound(String code, String message) {
        return new RetailflowException(code, message, HttpStatus.NOT_FOUND.value());
    }

    public String memberName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return membershipRepository
                .findByUserIdAndTenantId(userId, TenantContext.requireTenantId())
                .map(TenantMembership::getUser)
                .map(user -> user.getFullName())
                .orElse("Staff");
    }
}
