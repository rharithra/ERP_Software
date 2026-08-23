package in.retailflow.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.retailflow.api.identity.domain.TenantRole;
import in.retailflow.api.security.jwt.JwtService;
import in.retailflow.api.support.AuthTestSupport;
import in.retailflow.api.support.AuthTestSupport.SignupResult;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
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
    void eachOwnerOnlySeesTheirOwnCompany() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Gupta Garments");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Iyer Mobile Hub");

        JsonNode tenantA = mapper.readTree(rest.exchange(
                        "/api/v1/tenant",
                        HttpMethod.GET,
                        new HttpEntity<>(AuthTestSupport.bearer(a.token())),
                        String.class)
                .getBody());
        JsonNode tenantB = mapper.readTree(rest.exchange(
                        "/api/v1/tenant",
                        HttpMethod.GET,
                        new HttpEntity<>(AuthTestSupport.bearer(b.token())),
                        String.class)
                .getBody());

        assertThat(tenantA.path("data").path("name").asText()).isEqualTo("Gupta Garments");
        assertThat(tenantB.path("data").path("name").asText()).isEqualTo("Iyer Mobile Hub");
        assertThat(tenantA.path("data").path("id").asText()).isNotEqualTo(tenantB.path("data").path("id").asText());
    }

    @Test
    void rowLevelSecurityHidesOtherTenants() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Patel Wholesale");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Khan General Store");

        List<String> namesForA = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', 'off', true)");
                statement.execute("SELECT set_config('app.current_tenant_id', '" + a.tenantId() + "', true)");
                ResultSet rs = statement.executeQuery("SELECT name FROM tenants");
                List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
                connection.rollback();
                return names;
            }
        });

        assertThat(namesForA).containsExactly("Patel Wholesale");

        List<String> namesForB = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', 'off', true)");
                statement.execute("SELECT set_config('app.current_tenant_id', '" + b.tenantId() + "', true)");
                ResultSet rs = statement.executeQuery("SELECT name FROM tenants");
                List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
                connection.rollback();
                return names;
            }
        });

        assertThat(namesForB).containsExactly("Khan General Store");
    }

    @Test
    void cashierCannotUpdateCompanyProfile() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Reddy Supermarket");
        UUID cashierUserId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        String cashierEmail = "cashier-" + UUID.randomUUID() + "@retailflow.test";
        String hash = passwordEncoder.encode("CashierPass9");

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            connection.setAutoCommit(false);
            try (PreparedStatement bypass = connection.prepareStatement("SELECT set_config('app.bypass_rls', 'on', true)");
                    PreparedStatement user = connection.prepareStatement(
                            "INSERT INTO users (id, email, password_hash, full_name) VALUES (?, ?, ?, ?)");
                    PreparedStatement membership = connection.prepareStatement(
                            "INSERT INTO tenant_memberships (id, tenant_id, user_id, role) VALUES (?, ?::uuid, ?, ?)")) {
                bypass.execute();
                user.setObject(1, cashierUserId);
                user.setString(2, cashierEmail);
                user.setString(3, hash);
                user.setString(4, "Ravi Cashier");
                user.executeUpdate();
                membership.setObject(1, membershipId);
                membership.setString(2, owner.tenantId());
                membership.setObject(3, cashierUserId);
                membership.setString(4, TenantRole.CASHIER.name());
                membership.executeUpdate();
                connection.commit();
                return null;
            }
        });

        String cashierToken = jwtService.issueToken(
                cashierUserId, cashierEmail, UUID.fromString(owner.tenantId()), TenantRole.CASHIER);

        ResponseEntity<String> get = rest.exchange(
                "/api/v1/tenant",
                HttpMethod.GET,
                new HttpEntity<>(AuthTestSupport.bearer(cashierToken)),
                String.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> put = rest.exchange(
                "/api/v1/tenant",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Hacked Name"), AuthTestSupport.bearer(cashierToken)),
                String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
