package in.retailflow.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.retailflow.api.identity.domain.TenantRole;
import in.retailflow.api.security.jwt.JwtService;
import in.retailflow.api.support.AuthTestSupport;
import in.retailflow.api.support.AuthTestSupport.SignupResult;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Milestone 1 company-profile authorization.
 *
 * <p>MANAGER can GET and PUT {@code /api/v1/tenant} under the current {@code @PreAuthorize}
 * policy. Signup and the UI only create OWNER. These tests insert MANAGER with SQL.
 * There is no manager invitation flow in this milestone.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ManagerAuthorizationIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void managerCanReadAndUpdateCompanyProfile() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Verma Fashion");
        String managerEmail = "manager-" + UUID.randomUUID() + "@retailflow.test";
        UUID managerUserId = AuthTestSupport.insertMembership(
                jdbcTemplate,
                owner.tenantId(),
                managerEmail,
                passwordEncoder.encode("ManagerPass9"),
                "Priya Manager",
                TenantRole.MANAGER.name());

        String managerToken = jwtService.issueToken(
                managerUserId, managerEmail, UUID.fromString(owner.tenantId()), TenantRole.MANAGER);

        ResponseEntity<String> get = rest.exchange(
                "/api/v1/tenant",
                HttpMethod.GET,
                new HttpEntity<>(AuthTestSupport.bearer(managerToken)),
                String.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode getBody = mapper.readTree(get.getBody());
        assertThat(getBody.path("data").path("id").asText()).isEqualTo(owner.tenantId());
        assertThat(getBody.path("data").path("name").asText()).isEqualTo("Verma Fashion");

        ResponseEntity<String> put = rest.exchange(
                "/api/v1/tenant",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Verma Fashion West"), AuthTestSupport.bearer(managerToken)),
                String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode putBody = mapper.readTree(put.getBody());
        assertThat(putBody.path("data").path("name").asText()).isEqualTo("Verma Fashion West");
        assertThat(putBody.path("data").path("id").asText()).isEqualTo(owner.tenantId());
    }
}
