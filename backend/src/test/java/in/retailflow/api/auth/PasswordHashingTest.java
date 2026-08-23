package in.retailflow.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordHashingTest {

    @Test
    void passwordsAreHashedWithBcryptAndVerified() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String raw = "ShopOwner@123";
        String hash = encoder.encode(raw);

        assertThat(hash).isNotEqualTo(raw);
        assertThat(hash).startsWith("$2");
        assertThat(encoder.matches(raw, hash)).isTrue();
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
    }
}
