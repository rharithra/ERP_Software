package in.retailflow.api.security.jwt;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class JwtSecretValidator {

    private final JwtProperties jwtProperties;

    public JwtSecretValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    void validate() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is required. Set it in the environment. Do not use a committed default in production.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }
        if (jwtProperties.isRejectKnownDevSecret() && JwtProperties.LOCAL_DEV_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET must not be the documented local-development default. "
                            + "Generate a unique secret for Docker/production.");
        }
    }
}
