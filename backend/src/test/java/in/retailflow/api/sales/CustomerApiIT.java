package in.retailflow.api.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.retailflow.api.identity.domain.TenantRole;
import in.retailflow.api.security.jwt.JwtService;
import in.retailflow.api.support.AuthTestSupport;
import in.retailflow.api.support.AuthTestSupport.SignupResult;
import java.util.HashMap;
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
class CustomerApiIT {

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
    void ownerCreatesUpdatesSearchesAndDeactivatesCustomer() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Customer Owner Shop");

        ResponseEntity<String> created = post(
                owner.token(),
                "/api/v1/customers",
                Map.of(
                        "name", "Meena Iyer",
                        "phone", "9876500001",
                        "email", "meena@example.com",
                        "gstin", "27AABCU9603R1ZX"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode customer = mapper.readTree(created.getBody()).path("data");
        String id = customer.path("id").asText();
        assertThat(customer.path("name").asText()).isEqualTo("Meena Iyer");
        assertThat(customer.path("active").asBoolean()).isTrue();

        JsonNode byName = mapper.readTree(get(owner.token(), "/api/v1/customers?q=Meena").getBody());
        assertThat(byName.path("data").path("items")).hasSize(1);
        JsonNode byPhone = mapper.readTree(get(owner.token(), "/api/v1/customers?q=9876500001").getBody());
        assertThat(byPhone.path("data").path("items")).hasSize(1);
        JsonNode byEmail = mapper.readTree(get(owner.token(), "/api/v1/customers?q=meena@example.com").getBody());
        assertThat(byEmail.path("data").path("items")).hasSize(1);

        ResponseEntity<String> updated = put(
                owner.token(),
                "/api/v1/customers/" + id,
                Map.of("name", "Meena Iyer", "phone", "9876500001", "address", "Pune"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(updated.getBody()).path("data").path("address").asText()).isEqualTo("Pune");

        assertThat(patch(owner.token(), "/api/v1/customers/" + id + "/status", Map.of("active", false))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode inactive = mapper.readTree(get(owner.token(), "/api/v1/customers?active=false").getBody());
        assertThat(inactive.path("data").path("items")).hasSize(1);

        JsonNode summary = mapper.readTree(get(owner.token(), "/api/v1/customers/summary").getBody()).path("data");
        assertThat(summary.path("totalCustomers").asInt()).isEqualTo(1);
        assertThat(summary.path("inactiveCustomers").asInt()).isEqualTo(1);
    }

    @Test
    void duplicatePhoneRejectedAndInvalidEmailRejected() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Customer Unique Shop");
        assertThat(post(owner.token(), "/api/v1/customers", Map.of("name", "First", "phone", "9000000001"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> duplicate =
                post(owner.token(), "/api/v1/customers", Map.of("name", "Second", "phone", "9000000001"));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapper.readTree(duplicate.getBody()).path("error").path("code").asText())
                .isEqualTo("CUSTOMER_PHONE_TAKEN");

        ResponseEntity<String> badEmail =
                post(owner.token(), "/api/v1/customers", Map.of("name", "Bad Email", "email", "not-an-email"));
        assertThat(badEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cashierCanViewButNotMutateAndForeignCustomerIsIsolated() throws Exception {
        SignupResult ownerA = AuthTestSupport.signup(rest, mapper, "Customer Shop A");
        SignupResult ownerB = AuthTestSupport.signup(rest, mapper, "Customer Shop B");
        String customerB = mapper.readTree(post(ownerB.token(), "/api/v1/customers", Map.of("name", "Foreign Buyer"))
                        .getBody())
                .path("data")
                .path("id")
                .asText();
        JsonNode created = mapper.readTree(post(ownerA.token(), "/api/v1/customers", Map.of("name", "Local Buyer"))
                        .getBody())
                .path("data");
        String customerA = created.path("id").asText();

        assertThat(get(ownerA.token(), "/api/v1/customers/" + customerB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> injected = new HashMap<>();
        injected.put("name", "Injected");
        injected.put("tenantId", ownerB.tenantId());
        JsonNode ignored = mapper.readTree(post(ownerA.token(), "/api/v1/customers", injected).getBody()).path("data");
        UUID storedTenant = jdbcTemplate.execute((ConnectionCallback<UUID>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', 'on', true)");
                try (var rs = statement.executeQuery(
                        "SELECT tenant_id FROM customers WHERE id = '" + ignored.path("id").asText() + "'")) {
                    rs.next();
                    UUID tenantId = rs.getObject(1, UUID.class);
                    connection.rollback();
                    return tenantId;
                }
            }
        });
        assertThat(storedTenant).isEqualTo(UUID.fromString(ownerA.tenantId()));

        String cashier = cashierToken(ownerA);
        assertThat(get(cashier, "/api/v1/customers").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(cashier, "/api/v1/customers/" + customerA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post(cashier, "/api/v1/customers", Map.of("name", "Cashier Customer")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(put(cashier, "/api/v1/customers/" + customerA, Map.of("name", "Nope")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String manager = managerToken(ownerA);
        assertThat(put(manager, "/api/v1/customers/" + customerA, Map.of("name", "Local Buyer Updated"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
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
