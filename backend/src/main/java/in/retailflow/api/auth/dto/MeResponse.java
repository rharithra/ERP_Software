package in.retailflow.api.auth.dto;

public record MeResponse(
        String id, String email, String fullName, String role, AuthResponse.TenantSummary tenant) {}
