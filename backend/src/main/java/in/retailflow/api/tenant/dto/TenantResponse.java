package in.retailflow.api.tenant.dto;

import in.retailflow.api.tenant.domain.BusinessType;
import in.retailflow.api.tenant.domain.SalesMode;

public record TenantResponse(
        String id,
        String name,
        String legalName,
        String gstin,
        String phone,
        String email,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String currency,
        String timezone,
        BusinessType businessType,
        SalesMode salesMode) {}
