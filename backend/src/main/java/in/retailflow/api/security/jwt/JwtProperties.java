package in.retailflow.api.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "retailflow.jwt")
public class JwtProperties {

    /**
     * Documented development-only secret used by the {@code local} Spring profile
     * when {@code JWT_SECRET} is unset. Production and Docker must reject this value.
     */
    public static final String LOCAL_DEV_SECRET = "local-dev-only-change-me-use-a-64-character-secret-key!!";

    private String secret;
    private long expirationMs = 28_800_000;
    private boolean rejectKnownDevSecret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public boolean isRejectKnownDevSecret() {
        return rejectKnownDevSecret;
    }

    public void setRejectKnownDevSecret(boolean rejectKnownDevSecret) {
        this.rejectKnownDevSecret = rejectKnownDevSecret;
    }
}
