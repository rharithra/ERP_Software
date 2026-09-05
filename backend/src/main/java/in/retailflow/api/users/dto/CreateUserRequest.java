package in.retailflow.api.users.dto;

import in.retailflow.api.identity.domain.TenantRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 2, max = 150) String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull TenantRole role,
        @NotBlank @Size(min = 8, max = 72) String temporaryPassword) {}
