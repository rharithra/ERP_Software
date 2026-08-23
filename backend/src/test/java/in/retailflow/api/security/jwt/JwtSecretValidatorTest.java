package in.retailflow.api.security.jwt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtSecretValidatorTest {

    @Test
    void rejectsBlankSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("   ");
        JwtSecretValidator validator = new JwtSecretValidator(properties);
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKnownDevSecretWhenDockerOrProductionFlagIsOn() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(JwtProperties.LOCAL_DEV_SECRET);
        properties.setRejectKnownDevSecret(true);
        JwtSecretValidator validator = new JwtSecretValidator(properties);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local-development default");
    }

    @Test
    void allowsKnownDevSecretOnlyWhenFlagIsOff() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(JwtProperties.LOCAL_DEV_SECRET);
        properties.setRejectKnownDevSecret(false);
        new JwtSecretValidator(properties).validate();
    }
}
