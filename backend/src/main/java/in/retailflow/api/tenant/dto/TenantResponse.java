package in.retailflow.api.tenant.dto;

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
        String timezone) {}
