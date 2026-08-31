package in.retailflow.api.pipeline.dto;

import in.retailflow.api.pipeline.domain.LeadPriority;
import in.retailflow.api.pipeline.domain.LeadSource;
import in.retailflow.api.pipeline.domain.LeadStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LeadResponse(
        String id,
        String leadNumber,
        String name,
        String phone,
        String email,
        String companyName,
        String address,
        LeadSource source,
        String requirement,
        BigDecimal expectedValue,
        LocalDate expectedCloseDate,
        String assignedTo,
        String assignedToName,
        LeadPriority priority,
        LeadStatus status,
        String notes,
        String lostReason,
        String convertedCustomerId,
        String convertedCustomerName,
        Instant createdAt,
        Instant updatedAt) {}
