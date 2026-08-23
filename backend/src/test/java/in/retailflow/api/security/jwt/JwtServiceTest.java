package in.retailflow.api.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.retailflow.api.identity.domain.TenantRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void issuesAndParsesClaimsWithoutSensitiveFields() {
        JwtService jwtService = new JwtService("unit-test-secret-key-of-at-least-32-chars!", 60_000);
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = jwtService.issueToken(userId, "owner@kirana.example", tenantId, TenantRole.OWNER);
        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("tenantId", String.class)).isEqualTo(tenantId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("OWNER");
        assertThat(claims.get("email", String.class)).isEqualTo("owner@kirana.example");
        assertThat(claims.get("password")).isNull();
        assertThat(claims.get("passwordHash")).isNull();
    }

    @Test
    void rejectsExpiredTokens() {
        JwtService jwtService = new JwtService("unit-test-secret-key-of-at-least-32-chars!", 1);
        String token = jwtService.issueToken(UUID.randomUUID(), "a@b.com", UUID.randomUUID(), TenantRole.CASHIER);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void requiresLongSecret() {
        assertThatThrownBy(() -> new JwtService("short", 1000)).isInstanceOf(IllegalStateException.class);
    }
}
