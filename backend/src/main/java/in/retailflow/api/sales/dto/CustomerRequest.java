package in.retailflow.api.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank @Size(min = 2, max = 200) String name,
        @Size(max = 20) String phone,
        @Size(max = 320) String email,
        @Size(max = 500) String address,
        @Size(max = 15) String gstin,
        @Size(max = 1000) String notes) {}
