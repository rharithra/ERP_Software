package in.retailflow.api.security.tenant;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<TenantPrincipal> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(TenantPrincipal principal) {
        CURRENT.set(principal);
    }

    public static TenantPrincipal require() {
        TenantPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw new IllegalStateException("Tenant context is not established");
        }
        return principal;
    }

    public static TenantPrincipal get() {
        return CURRENT.get();
    }

    public static UUID requireTenantId() {
        return require().tenantId();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record TenantPrincipal(UUID userId, UUID tenantId, String email, String role, String fullName) {}
}
