package in.retailflow.api.security.jwt;

import in.retailflow.api.identity.domain.TenantRole;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    @Autowired
    public JwtService(JwtProperties jwtProperties) {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = jwtProperties.getExpirationMs();
    }

    JwtService(String secret, long expirationMs) {
        this(properties(secret, expirationMs));
    }

    private static JwtProperties properties(String secret, long expirationMs) {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(secret);
        jwtProperties.setExpirationMs(expirationMs);
        return jwtProperties;
    }

    public String issueToken(UUID userId, String email, UUID tenantId, TenantRole role) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("tenantId", tenantId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
