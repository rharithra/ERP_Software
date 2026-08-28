package in.retailflow.api.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @NotBlank @Size(min = 2, max = 200) String name,
        @Size(max = 200) String legalName,
        @Pattern(
                        regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$",
                        message = "GSTIN must be a valid 15-character GST identification number")
                String gstin,
        @Size(max = 20) String phone,
        @Email @Size(max = 320) String email,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 10) String pincode,
        String businessType,
        String salesMode) {}
