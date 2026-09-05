package in.retailflow.api.users;

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
class UserManagementIT {

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
    void ownerCanManageStaffAndCannotModifySelfOrCreateOwner() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Team Store");
        String token = owner.token();

        JsonNode created = data(post(
                token,
                "/api/v1/users",
                Map.of(
                        "fullName", "Meena Iyer",
                        "email", "meena-" + UUID.randomUUID() + "@retailflow.test",
                        "role", "MANAGER",
                        "temporaryPassword", "TempPass99")));
        assertThat(created.path("role").asText()).isEqualTo("MANAGER");
        assertThat(created.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.has("password")).isFalse();
        assertThat(created.has("passwordHash")).isFalse();
        String managerId = created.path("id").asText();

        JsonNode cashier = data(post(
                token,
                "/api/v1/users",
                Map.of(
                        "fullName", "Ravi Cash",
                        "email", "ravi-" + UUID.randomUUID() + "@retailflow.test",
                        "role", "CASHIER",
                        "temporaryPassword", "CashPass99")));
        String cashierId = cashier.path("id").asText();

        JsonNode list = data(get(token, "/api/v1/users"));
        assertThat(list.size()).isGreaterThanOrEqualTo(3);

        JsonNode asCashier = data(patch(token, "/api/v1/users/" + managerId + "/role", Map.of("role", "CASHIER")));
        assertThat(asCashier.path("role").asText()).isEqualTo("CASHIER");
        JsonNode asManager = data(patch(token, "/api/v1/users/" + managerId + "/role", Map.of("role", "MANAGER")));
        assertThat(asManager.path("role").asText()).isEqualTo("MANAGER");

        assertThat(post(
                        token,
                        "/api/v1/users/" + cashierId + "/reset-password",
                        Map.of("temporaryPassword", "NewTemp99"))
                .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode inactive = data(patch(token, "/api/v1/users/" + cashierId + "/status", Map.of("status", "INACTIVE")));
        assertThat(inactive.path("status").asText()).isEqualTo("INACTIVE");
        JsonNode active = data(patch(token, "/api/v1/users/" + cashierId + "/status", Map.of("status", "ACTIVE")));
        assertThat(active.path("status").asText()).isEqualTo("ACTIVE");

        ResponseEntity<String> createOwner = post(
                token,
                "/api/v1/users",
                Map.of(
                        "fullName", "Second Owner",
                        "email", "owner2-" + UUID.randomUUID() + "@retailflow.test",
                        "role", "OWNER",
                        "temporaryPassword", "OwnerPass9"));
        assertThat(createOwner.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.readTree(createOwner.getBody()).path("error").path("code").asText()).isEqualTo("INVALID_ROLE");

        ResponseEntity<String> selfRole =
                patch(token, "/api/v1/users/" + owner.userId() + "/role", Map.of("role", "MANAGER"));
        assertThat(selfRole.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapper.readTree(selfRole.getBody()).path("error").path("code").asText())
                .isEqualTo("CANNOT_MODIFY_SELF");

        ResponseEntity<String> selfOff =
                patch(token, "/api/v1/users/" + owner.userId() + "/status", Map.of("status", "INACTIVE"));
        assertThat(selfOff.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void managerAndCashierCannotManageUsers() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Locked Store");
        String manager = staffToken(owner, TenantRole.MANAGER, "Mgr");
        String cashier = staffToken(owner, TenantRole.CASHIER, "Cash");
        Map<String, Object> body = Map.of(
                "fullName", "Nope",
                "email", "nope-" + UUID.randomUUID() + "@retailflow.test",
                "role", "CASHIER",
                "temporaryPassword", "NoPass999");
        assertThat(get(manager, "/api/v1/users").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(manager, "/api/v1/users", body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(cashier, "/api/v1/users").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(cashier, "/api/v1/users", body).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patch(manager, "/api/v1/users/" + owner.userId() + "/role", Map.of("role", "CASHIER"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patch(cashier, "/api/v1/users/" + owner.userId() + "/status", Map.of("status", "INACTIVE"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(manager, "/api/v1/users/" + owner.userId() + "/reset-password", Map.of("temporaryPassword", "NoPass999"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantIsolationBlocksCrossTenantUserIds() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Store A");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Store B");
        JsonNode staff = data(post(
                a.token(),
                "/api/v1/users",
                Map.of(
                        "fullName", "Hidden",
                        "email", "hidden-" + UUID.randomUUID() + "@retailflow.test",
                        "role", "CASHIER",
                        "temporaryPassword", "HidePass9")));
        String staffId = staff.path("id").asText();
        assertThat(get(b.token(), "/api/v1/users/" + staffId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(patch(b.token(), "/api/v1/users/" + staffId + "/role", Map.of("role", "MANAGER")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(patch(b.token(), "/api/v1/users/" + staffId + "/status", Map.of("status", "INACTIVE")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(b.token(), "/api/v1/users/" + staffId + "/reset-password", Map.of("temporaryPassword", "StealPass9"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void inactiveUserCannotLoginAndRoleChangeAppliesOnExistingJwt() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Session Store");
        JsonNode staff = data(post(
                owner.token(),
                "/api/v1/users",
                Map.of(
                        "fullName", "Priya",
                        "email", "priya-" + UUID.randomUUID() + "@retailflow.test",
                        "role", "CASHIER",
                        "temporaryPassword", "PriyaPass9")));
        String email = staff.path("email").asText();
        String staffId = staff.path("id").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> login = rest.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", "PriyaPass9"), headers),
                String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String staffToken = mapper.readTree(login.getBody()).path("data").path("accessToken").asText();
        assertThat(get(staffToken, "/api/v1/leads").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        data(patch(owner.token(), "/api/v1/users/" + staffId + "/role", Map.of("role", "MANAGER")));
        assertThat(get(staffToken, "/api/v1/leads").getStatusCode()).isEqualTo(HttpStatus.OK);

        data(patch(owner.token(), "/api/v1/users/" + staffId + "/status", Map.of("status", "INACTIVE")));
        ResponseEntity<String> blocked = rest.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", "PriyaPass9"), headers),
                String.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(mapper.readTree(blocked.getBody()).path("error").path("code").asText()).isEqualTo("ACCOUNT_INACTIVE");
        assertThat(get(staffToken, "/api/v1/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        data(patch(owner.token(), "/api/v1/users/" + staffId + "/status", Map.of("status", "ACTIVE")));
        ResponseEntity<String> again = rest.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", "PriyaPass9"), headers),
                String.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String staffToken(SignupResult owner, TenantRole role, String name) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@retailflow.test";
        UUID userId = AuthTestSupport.insertMembership(
                jdbcTemplate, owner.tenantId(), email, passwordEncoder.encode("RolePass9"), name, role.name());
        return jwtService.issueToken(userId, email, UUID.fromString(owner.tenantId()), role);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return mapper.readTree(response.getBody()).path("data");
    }

    private ResponseEntity<String> get(String token, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(AuthTestSupport.bearer(token)), String.class);
    }

    private ResponseEntity<String> post(String token, String path, Object body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, AuthTestSupport.bearer(token)), String.class);
    }

    private ResponseEntity<String> patch(String token, String path, Object body) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, AuthTestSupport.bearer(token)), String.class);
    }
}
