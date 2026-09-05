package in.retailflow.api.users.dto;

import in.retailflow.api.identity.domain.TenantRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull TenantRole role) {}
