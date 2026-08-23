package in.retailflow.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.retailflow.api.identity.domain.TenantRole;
import in.retailflow.api.security.jwt.JwtService;
import in.retailflow.api.support.AuthTestSupport;
import in.retailflow.api.support.AuthTestSupport.SignupResult;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TenantIsolationIT {

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
    void tenantACannotReadTenantB() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Gupta Garments");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Iyer Mobile Hub");

        JsonNode tenantA = mapper.readTree(getTenant(a.token()).getBody());
        JsonNode tenantB = mapper.readTree(getTenant(b.token()).getBody());

        assertThat(tenantA.path("data").path("name").asText()).isEqualTo("Gupta Garments");
        assertThat(tenantB.path("data").path("name").asText()).isEqualTo("Iyer Mobile Hub");
        assertThat(tenantA.path("data").path("id").asText()).isEqualTo(a.tenantId());
        assertThat(tenantA.path("data").path("id").asText()).isNotEqualTo(b.tenantId());
    }

    @Test
    void tenantACannotModifyTenantB() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Patel Wholesale");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Khan General Store");

        ResponseEntity<String> put = rest.exchange(
                "/api/v1/tenant",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Hijacked Store"), AuthTestSupport.bearer(a.token())),
                String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updatedA = mapper.readTree(put.getBody());
        assertThat(updatedA.path("data").path("id").asText()).isEqualTo(a.tenantId());
        assertThat(updatedA.path("data").path("name").asText()).isEqualTo("Hijacked Store");

        JsonNode tenantB = mapper.readTree(getTenant(b.token()).getBody());
        assertThat(tenantB.path("data").path("name").asText()).isEqualTo("Khan General Store");
        assertThat(tenantB.path("data").path("id").asText()).isEqualTo(b.tenantId());
    }

    @Test
    void forgedJwtWithUserAAndTenantBIsRejected() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Mehta Textiles");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Nair Pharmacy");

        String forged = jwtService.issueToken(
                UUID.fromString(a.userId()), a.email(), UUID.fromString(b.tenantId()), TenantRole.OWNER);

        assertThat(getTenant(forged).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> put = rest.exchange(
                "/api/v1/tenant",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Forged Name"), AuthTestSupport.bearer(forged)),
                String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        JsonNode tenantB = mapper.readTree(getTenant(b.token()).getBody());
        assertThat(tenantB.path("data").path("name").asText()).isEqualTo("Nair Pharmacy");
    }

    @Test
    void requestTenantIdNeverChangesEffectiveTenant() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Bose Stationery");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Das Hardware");

        ResponseEntity<String> query = rest.exchange(
                "/api/v1/tenant?tenantId=" + b.tenantId(),
                HttpMethod.GET,
                new HttpEntity<>(AuthTestSupport.bearer(a.token())),
                String.class);
        assertThat(query.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode queryBody = mapper.readTree(query.getBody());
        assertThat(queryBody.path("data").path("id").asText()).isEqualTo(a.tenantId());
        assertThat(queryBody.path("data").path("name").asText()).isEqualTo("Bose Stationery");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "Bose Stationery Updated");
        body.put("tenantId", b.tenantId());
        body.put("id", b.tenantId());
        ResponseEntity<String> put = rest.exchange(
                "/api/v1/tenant",
                HttpMethod.PUT,
                new HttpEntity<>(body, AuthTestSupport.bearer(a.token())),
                String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode putBody = mapper.readTree(put.getBody());
        assertThat(putBody.path("data").path("id").asText()).isEqualTo(a.tenantId());
        assertThat(putBody.path("data").path("name").asText()).isEqualTo("Bose Stationery Updated");

        ResponseEntity<String> path = rest.exchange(
                "/api/v1/tenant/" + b.tenantId(),
                HttpMethod.GET,
                new HttpEntity<>(AuthTestSupport.bearer(a.token())),
                String.class);
        assertThat(path.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode tenantB = mapper.readTree(getTenant(b.token()).getBody());
        assertThat(tenantB.path("data").path("name").asText()).isEqualTo("Das Hardware");
        assertThat(tenantB.path("data").path("id").asText()).isEqualTo(b.tenantId());
    }

    @Test
    void emptyRlsTenantContextFailsClosed() throws Exception {
        SignupResult created = AuthTestSupport.signup(rest, mapper, "Empty Context Store");
        assertThat(created.tenantId()).isNotBlank();

        List<String> names = queryTenantNames("", false);
        assertThat(names).isEmpty();
    }

    @Test
    void rlsBlocksCrossTenantSqlAccess() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Joshi Kirana");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Kapoor Electronics");

        assertThat(queryTenantNames(a.tenantId(), false)).containsExactly("Joshi Kirana");
        assertThat(queryTenantNames(b.tenantId(), false)).containsExactly("Kapoor Electronics");

        Integer updated = jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', 'off', true)");
                statement.execute("SELECT set_config('app.current_tenant_id', '" + a.tenantId() + "', true)");
                int count = statement.executeUpdate(
                        "UPDATE tenants SET name = 'Should Not Apply' WHERE id = '" + b.tenantId() + "'");
                connection.rollback();
                return count;
            }
        });
        assertThat(updated).isZero();
        assertThat(queryTenantNames(b.tenantId(), false)).containsExactly("Kapoor Electronics");
    }

    @Test
    void cashierCannotUpdateCompanyProfile() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Reddy Supermarket");
        String cashierEmail = "cashier-" + UUID.randomUUID() + "@retailflow.test";
        UUID cashierUserId = AuthTestSupport.insertMembership(
                jdbcTemplate,
                owner.tenantId(),
                cashierEmail,
                passwordEncoder.encode("CashierPass9"),
                "Ravi Cashier",
                TenantRole.CASHIER.name());

        String cashierToken = jwtService.issueToken(
                cashierUserId, cashierEmail, UUID.fromString(owner.tenantId()), TenantRole.CASHIER);

        assertThat(getTenant(cashierToken).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> put = rest.exchange(
                "/api/v1/tenant",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Hacked Name"), AuthTestSupport.bearer(cashierToken)),
                String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<String> getTenant(String token) {
        return rest.exchange(
                "/api/v1/tenant", HttpMethod.GET, new HttpEntity<>(AuthTestSupport.bearer(token)), String.class);
    }

    private List<String> queryTenantNames(String tenantId, boolean bypass) {
        return jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', '" + (bypass ? "on" : "off") + "', true)");
                statement.execute("SELECT set_config('app.current_tenant_id', '" + tenantId + "', true)");
                ResultSet rs = statement.executeQuery("SELECT name FROM tenants");
                List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
                connection.rollback();
                return names;
            }
        });
    }
}
