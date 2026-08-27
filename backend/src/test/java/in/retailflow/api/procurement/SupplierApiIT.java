package in.retailflow.api.procurement;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.retailflow.api.identity.domain.TenantRole;
import in.retailflow.api.security.jwt.JwtService;
import in.retailflow.api.support.AuthTestSupport;
import in.retailflow.api.support.AuthTestSupport.SignupResult;
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
class SupplierApiIT {

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
    void ownerCreatesListsUpdatesAndDeactivatesSupplier() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Supplier Owner Shop");

        ResponseEntity<String> created = post(
                owner.token(),
                "/api/v1/suppliers",
                Map.of(
                        "name", "ABC Distributors",
                        "contactPerson", "Ramesh",
                        "phone", "9876543210",
                        "gstin", "27AABCU9603R1ZX"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode supplier = mapper.readTree(created.getBody()).path("data");
        String id = supplier.path("id").asText();
        assertThat(supplier.path("name").asText()).isEqualTo("ABC Distributors");
        assertThat(supplier.path("active").asBoolean()).isTrue();

        JsonNode listed = mapper.readTree(get(owner.token(), "/api/v1/suppliers?q=ABC").getBody());
        assertThat(listed.path("data").path("items")).hasSize(1);
        assertThat(listed.path("data").path("page").asInt()).isEqualTo(1);

        ResponseEntity<String> updated = put(
                owner.token(),
                "/api/v1/suppliers/" + id,
                Map.of("name", "ABC Distributors", "contactPerson", "Suresh", "phone", "9876543210"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(updated.getBody()).path("data").path("contactPerson").asText()).isEqualTo("Suresh");

        ResponseEntity<String> deactivated =
                patch(owner.token(), "/api/v1/suppliers/" + id + "/status", Map.of("active", false));
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(deactivated.getBody()).path("data").path("active").asBoolean()).isFalse();

        JsonNode summary = mapper.readTree(get(owner.token(), "/api/v1/suppliers/summary").getBody()).path("data");
        assertThat(summary.path("totalSuppliers").asInt()).isEqualTo(1);
        assertThat(summary.path("inactiveSuppliers").asInt()).isEqualTo(1);
    }

    @Test
    void duplicateNameAndTenantIsolation() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Supplier A Shop");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Supplier B Shop");

        assertThat(post(a.token(), "/api/v1/suppliers", Map.of("name", "Same Name")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(post(a.token(), "/api/v1/suppliers", Map.of("name", "same name")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<String> bCreated = post(b.token(), "/api/v1/suppliers", Map.of("name", "Same Name"));
        assertThat(bCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bId = mapper.readTree(bCreated.getBody()).path("data").path("id").asText();

        assertThat(mapper.readTree(get(a.token(), "/api/v1/suppliers").getBody()).path("data").path("items"))
                .hasSize(1);
        assertThat(mapper.readTree(get(b.token(), "/api/v1/suppliers").getBody()).path("data").path("items"))
                .hasSize(1);
        assertThat(get(a.token(), "/api/v1/suppliers/" + bId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put(a.token(), "/api/v1/suppliers/" + bId, Map.of("name", "Stolen")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> injected = Map.of("name", "Local Vendor", "tenantId", b.tenantId());
        ResponseEntity<String> created = post(a.token(), "/api/v1/suppliers", injected);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = mapper.readTree(created.getBody()).path("data").path("id").asText();
        UUID storedTenant = tenantOf("suppliers", id);
        assertThat(storedTenant).isEqualTo(UUID.fromString(a.tenantId()));

        List<String> visible = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', 'off', true)");
                statement.execute("SELECT set_config('app.current_tenant_id', '" + a.tenantId() + "', true)");
                var rs = statement.executeQuery("SELECT name FROM suppliers");
                java.util.ArrayList<String> found = new java.util.ArrayList<>();
                while (rs.next()) {
                    found.add(rs.getString(1));
                }
                connection.rollback();
                return found;
            }
        });
        assertThat(visible).contains("Same Name", "Local Vendor").doesNotContain("Stolen");
        assertThat(visible.stream().filter(name -> name.equals("Same Name")).count()).isEqualTo(1);
    }

    @Test
    void cashierCannotWriteSuppliers() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Cashier Supplier Shop");
        post(owner.token(), "/api/v1/suppliers", Map.of("name", "Visible Vendor"));
        String cashierToken = cashierToken(owner);
        assertThat(post(cashierToken, "/api/v1/suppliers", Map.of("name", "Nope")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(cashierToken, "/api/v1/suppliers").getStatusCode()).isEqualTo(HttpStatus.OK);

        String managerToken = managerToken(owner);
        assertThat(post(managerToken, "/api/v1/suppliers", Map.of("name", "Manager Vendor")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    private UUID tenantOf(String table, String id) {
        return jdbcTemplate.execute((ConnectionCallback<UUID>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', 'on', true)");
                try (var rs = statement.executeQuery("SELECT tenant_id FROM " + table + " WHERE id = '" + id + "'")) {
                    rs.next();
                    UUID tenantId = rs.getObject(1, UUID.class);
                    connection.rollback();
                    return tenantId;
                }
            }
        });
    }

    private String cashierToken(SignupResult owner) {
        String email = "cashier-" + UUID.randomUUID() + "@retailflow.test";
        UUID userId = AuthTestSupport.insertMembership(
                jdbcTemplate, owner.tenantId(), email, passwordEncoder.encode("CashierPass9"), "Ravi", TenantRole.CASHIER.name());
        return jwtService.issueToken(userId, email, UUID.fromString(owner.tenantId()), TenantRole.CASHIER);
    }

    private String managerToken(SignupResult owner) {
        String email = "manager-" + UUID.randomUUID() + "@retailflow.test";
        UUID userId = AuthTestSupport.insertMembership(
                jdbcTemplate, owner.tenantId(), email, passwordEncoder.encode("ManagerPass9"), "Priya", TenantRole.MANAGER.name());
        return jwtService.issueToken(userId, email, UUID.fromString(owner.tenantId()), TenantRole.MANAGER);
    }

    private ResponseEntity<String> get(String token, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(AuthTestSupport.bearer(token)), String.class);
    }

    private ResponseEntity<String> post(String token, String path, Object body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, AuthTestSupport.bearer(token)), String.class);
    }

    private ResponseEntity<String> put(String token, String path, Object body) {
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, AuthTestSupport.bearer(token)), String.class);
    }

    private ResponseEntity<String> patch(String token, String path, Object body) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, AuthTestSupport.bearer(token)), String.class);
    }
}
