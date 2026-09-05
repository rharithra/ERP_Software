package in.retailflow.api.users.dto;

import in.retailflow.api.identity.domain.MembershipStatus;
import in.retailflow.api.identity.domain.TenantRole;
import java.time.Instant;

public record TenantUserResponse(
        String id,
        String fullName,
        String email,
        TenantRole role,
        MembershipStatus status,
        Instant createdAt) {}
