package in.retailflow.api.auth.dto;

public record AuthResponse(
        String accessToken, String tokenType, long expiresInSeconds, AuthenticatedUser user) {

    public record AuthenticatedUser(
            String id, String email, String fullName, String role, TenantSummary tenant) {}

    public record TenantSummary(String id, String name, String currency, String timezone) {}
}
