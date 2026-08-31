package in.retailflow.api.pipeline.dto;

import in.retailflow.api.pipeline.domain.LeadPriority;
import in.retailflow.api.pipeline.domain.LeadSource;
import in.retailflow.api.pipeline.domain.LeadStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LeadRequest(
        @NotBlank @Size(min = 2, max = 200) String name,
        @Size(max = 20) String phone,
        @Size(max = 320) String email,
        @Size(max = 200) String companyName,
        @Size(max = 500) String address,
        @NotNull LeadSource source,
        @Size(max = 2000) String requirement,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal expectedValue,
        LocalDate expectedCloseDate,
        String assignedTo,
        LeadPriority priority,
        @Size(max = 2000) String notes) {}
