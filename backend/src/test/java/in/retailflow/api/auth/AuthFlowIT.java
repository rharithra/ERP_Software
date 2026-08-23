package in.retailflow.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.retailflow.api.support.AuthTestSupport;
import in.retailflow.api.support.AuthTestSupport.SignupResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void signupCreatesOwnerTenantAndReturnsJwt() throws Exception {
        SignupResult result = AuthTestSupport.signup(rest, mapper, "Sharma Kirana Store");
        assertThat(result.status()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(result.body().path("success").asBoolean()).isTrue();
        assertThat(result.role()).isEqualTo("OWNER");
        assertThat(result.token()).isNotBlank();

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            connection.setAutoCommit(false);
            try (var bypass = connection.prepareStatement("SELECT set_config('app.bypass_rls', 'on', true)");
                    var hashQuery = connection.prepareStatement(
                            "SELECT password_hash FROM users WHERE LOWER(email) = LOWER(?)");
                    var roleQuery = connection.prepareStatement(
                            "SELECT m.role FROM tenant_memberships m JOIN users u ON u.id = m.user_id WHERE LOWER(u.email) = LOWER(?)")) {
                bypass.execute();
                hashQuery.setString(1, result.email());
                try (var rs = hashQuery.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    String hash = rs.getString(1);
                    assertThat(hash).isNotEqualTo("SecurePass9");
                    assertThat(hash).startsWith("$2");
                }
                roleQuery.setString(1, result.email());
                try (var rs = roleQuery.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("OWNER");
                }
                connection.rollback();
                return null;
            }
        });
    }

    @Test
    void loginReturnsTokenAndMeRequiresAuthentication() throws Exception {
        SignupResult created = AuthTestSupport.signup(rest, mapper, "Mehta Electronics");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", created.email(), "password", "SecurePass9"), headers),
                String.class);
        JsonNode loginBody = mapper.readTree(login.getBody());
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginBody.path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();

        ResponseEntity<String> unauthenticated =
                rest.getForEntity("/api/v1/auth/me", String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> me = rest.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(AuthTestSupport.bearer(token)), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode meBody = mapper.readTree(me.getBody());
        assertThat(meBody.path("data").path("email").asText()).isEqualTo(created.email());
        assertThat(meBody.path("data").path("role").asText()).isEqualTo("OWNER");
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        SignupResult first = AuthTestSupport.signup(rest, mapper, "First Store");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> second = rest.postForEntity(
                "/api/v1/auth/signup",
                new HttpEntity<>(
                        Map.of(
                                "fullName", "Other Person",
                                "email", first.email(),
                                "password", "AnotherPass9",
                                "companyName", "Second Store"),
                        headers),
                String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = mapper.readTree(second.getBody());
        assertThat(body.path("error").path("code").asText()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void invalidLoginIsRejected() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", "nobody@retailflow.test", "password", "wrongpass"), headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
