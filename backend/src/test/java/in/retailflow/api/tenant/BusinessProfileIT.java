package in.retailflow.api.tenant;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BusinessProfileIT {

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
    void existingTenantRowGetsSafeDefaultsWhenColumnsOmitted() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            connection.setAutoCommit(false);
            try (java.sql.PreparedStatement bypass =
                            connection.prepareStatement("SELECT set_config('app.bypass_rls', 'on', true)");
                    java.sql.PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO tenants (id, name) VALUES (?, ?)");
                    java.sql.PreparedStatement select = connection.prepareStatement(
                            "SELECT business_type, sales_mode FROM tenants WHERE id = ?")) {
                bypass.execute();
                insert.setObject(1, tenantId);
                insert.setString(2, "Pre-M5.1 Shop");
                insert.executeUpdate();
                select.setObject(1, tenantId);
                try (java.sql.ResultSet rs = select.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("business_type")).isEqualTo("OTHER");
                    assertThat(rs.getString("sales_mode")).isEqualTo("HYBRID");
                }
                connection.commit();
            }
            return null;
        });
    }

    @Test
    void signupWithoutProfileDefaultsToOtherHybrid() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Legacy Compatible Shop");
        JsonNode tenant = mapper.readTree(get(owner.token(), "/api/v1/tenant").getBody()).path("data");
        assertThat(tenant.path("businessType").asText()).isEqualTo("OTHER");
        assertThat(tenant.path("salesMode").asText()).isEqualTo("HYBRID");
        JsonNode me = mapper.readTree(get(owner.token(), "/api/v1/auth/me").getBody()).path("data").path("tenant");
        assertThat(me.path("businessType").asText()).isEqualTo("OTHER");
        assertThat(me.path("salesMode").asText()).isEqualTo("HYBRID");
    }

    @Test
    void signupUsesRecommendationUnlessOwnerOverrides() throws Exception {
        SignupResult grocery = AuthTestSupport.signup(
                rest, mapper, "Daily Kirana", "GROCERY_SUPERMARKET", null);
        JsonNode groceryTenant = mapper.readTree(get(grocery.token(), "/api/v1/tenant").getBody()).path("data");
        assertThat(groceryTenant.path("businessType").asText()).isEqualTo("GROCERY_SUPERMARKET");
        assertThat(groceryTenant.path("salesMode").asText()).isEqualTo("QUICK_SALE");

        SignupResult hybridGrocery = AuthTestSupport.signup(
                rest, mapper, "Kirana Plus", "GROCERY_SUPERMARKET", "HYBRID");
        JsonNode hybridTenant = mapper.readTree(get(hybridGrocery.token(), "/api/v1/tenant").getBody()).path("data");
        assertThat(hybridTenant.path("salesMode").asText()).isEqualTo("HYBRID");

        SignupResult furniture = AuthTestSupport.signup(rest, mapper, "Woodworks", "FURNITURE", null);
        assertThat(mapper.readTree(get(furniture.token(), "/api/v1/tenant").getBody())
                        .path("data")
                        .path("salesMode")
                        .asText())
                .isEqualTo("PIPELINE");
    }

    @Test
    void ownerCanUpdateBusinessTypeAndSalesMode() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Profile Shop");
        Map<String, Object> body = profile(owner.token());
        body.put("businessType", "ELECTRONICS_COMPUTER");
        body.put("salesMode", "PIPELINE");
        ResponseEntity<String> updated = put(owner.token(), body);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = mapper.readTree(updated.getBody()).path("data");
        assertThat(data.path("businessType").asText()).isEqualTo("ELECTRONICS_COMPUTER");
        assertThat(data.path("salesMode").asText()).isEqualTo("PIPELINE");
        assertThat(data.path("name").asText()).isEqualTo("Profile Shop");
    }

    @Test
    void managerAndCashierCannotChangeSalesConfiguration() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Role Shop", "GROCERY_SUPERMARKET", "QUICK_SALE");
        String manager = token(owner, TenantRole.MANAGER, "Priya");
        String cashier = token(owner, TenantRole.CASHIER, "Ravi");

        Map<String, Object> body = profile(owner.token());
        body.put("businessType", "FURNITURE");
        body.put("salesMode", "PIPELINE");
        assertThat(put(manager, body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(put(cashier, body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Object> nameOnly = profile(owner.token());
        nameOnly.put("name", "Role Shop West");
        assertThat(put(manager, nameOnly).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode after = mapper.readTree(get(owner.token(), "/api/v1/tenant").getBody()).path("data");
        assertThat(after.path("name").asText()).isEqualTo("Role Shop West");
        assertThat(after.path("businessType").asText()).isEqualTo("GROCERY_SUPERMARKET");
        assertThat(after.path("salesMode").asText()).isEqualTo("QUICK_SALE");
        assertThat(get(cashier, "/api/v1/tenant").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void invalidEnumsAreRejected() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> signup = new HashMap<>();
        signup.put("fullName", "Ananya Sharma");
        signup.put("email", "bad-" + UUID.randomUUID() + "@retailflow.test");
        signup.put("password", "SecurePass9");
        signup.put("companyName", "Bad Type Shop");
        signup.put("businessType", "SPACE_STATION");
        ResponseEntity<String> created = rest.postForEntity("/api/v1/auth/signup", new HttpEntity<>(signup, headers), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Valid Then Invalid");
        Map<String, Object> body = profile(owner.token());
        body.put("salesMode", "MAGIC");
        ResponseEntity<String> put = put(owner.token(), body);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.readTree(put.getBody()).path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void tenantCannotReadOrModifyAnotherTenantConfiguration() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Shop A", "GROCERY_SUPERMARKET", "QUICK_SALE");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Shop B", "FURNITURE", "PIPELINE");

        JsonNode tenantA = mapper.readTree(get(a.token(), "/api/v1/tenant").getBody()).path("data");
        JsonNode tenantB = mapper.readTree(get(b.token(), "/api/v1/tenant").getBody()).path("data");
        assertThat(tenantA.path("id").asText()).isNotEqualTo(tenantB.path("id").asText());
        assertThat(tenantA.path("salesMode").asText()).isEqualTo("QUICK_SALE");
        assertThat(tenantB.path("salesMode").asText()).isEqualTo("PIPELINE");

        Map<String, Object> hijack = profile(a.token());
        hijack.put("name", "Hijacked");
        hijack.put("businessType", "FURNITURE");
        hijack.put("salesMode", "PIPELINE");
        assertThat(put(a.token(), hijack).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode stillB = mapper.readTree(get(b.token(), "/api/v1/tenant").getBody()).path("data");
        assertThat(stillB.path("name").asText()).isEqualTo("Shop B");
        assertThat(stillB.path("businessType").asText()).isEqualTo("FURNITURE");
        assertThat(stillB.path("salesMode").asText()).isEqualTo("PIPELINE");
    }

    private Map<String, Object> profile(String token) throws Exception {
        JsonNode data = mapper.readTree(get(token, "/api/v1/tenant").getBody()).path("data");
        Map<String, Object> body = new HashMap<>();
        body.put("name", data.path("name").asText());
        body.put("legalName", data.path("legalName").isNull() ? "" : data.path("legalName").asText());
        body.put("gstin", data.path("gstin").isNull() ? "" : data.path("gstin").asText());
        body.put("phone", data.path("phone").isNull() ? "" : data.path("phone").asText());
        body.put("email", data.path("email").isNull() ? "" : data.path("email").asText());
        body.put("addressLine1", data.path("addressLine1").isNull() ? "" : data.path("addressLine1").asText());
        body.put("addressLine2", data.path("addressLine2").isNull() ? "" : data.path("addressLine2").asText());
        body.put("city", data.path("city").isNull() ? "" : data.path("city").asText());
        body.put("state", data.path("state").isNull() ? "" : data.path("state").asText());
        body.put("pincode", data.path("pincode").isNull() ? "" : data.path("pincode").asText());
        body.put("businessType", data.path("businessType").asText());
        body.put("salesMode", data.path("salesMode").asText());
        return body;
    }

    private String token(SignupResult owner, TenantRole role, String name) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@retailflow.test";
        UUID userId = AuthTestSupport.insertMembership(
                jdbcTemplate, owner.tenantId(), email, passwordEncoder.encode("RolePass9"), name, role.name());
        return jwtService.issueToken(userId, email, UUID.fromString(owner.tenantId()), role);
    }

    private ResponseEntity<String> get(String token, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(AuthTestSupport.bearer(token)), String.class);
    }

    private ResponseEntity<String> put(String token, Object body) {
        return rest.exchange("/api/v1/tenant", HttpMethod.PUT, new HttpEntity<>(body, AuthTestSupport.bearer(token)), String.class);
    }
}
