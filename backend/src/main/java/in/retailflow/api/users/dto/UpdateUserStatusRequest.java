package in.retailflow.api.users.dto;

import in.retailflow.api.identity.domain.MembershipStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull MembershipStatus status) {}
