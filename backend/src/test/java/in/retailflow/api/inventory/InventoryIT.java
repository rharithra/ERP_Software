package in.retailflow.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.retailflow.api.identity.domain.TenantRole;
import in.retailflow.api.security.jwt.JwtService;
import in.retailflow.api.support.AuthTestSupport;
import in.retailflow.api.support.AuthTestSupport.SignupResult;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class InventoryIT {

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
    void openingStockAdjustmentsReorderAndStatus() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Nair Provisions");
        String productId = createProduct(owner.token(), "Tata Salt 1kg", "TATA-SALT-1");

        JsonNode beforeOpen = mapper.readTree(get(owner.token(), "/api/v1/inventory/" + productId).getBody()).path("data");
        assertThat(beforeOpen.path("quantity").decimalValue()).isEqualByComparingTo("0");
        assertThat(beforeOpen.path("status").asText()).isEqualTo("OUT_OF_STOCK");
        assertThat(beforeOpen.path("openingRecorded").asBoolean()).isFalse();

        ResponseEntity<String> opened = post(
                owner.token(), "/api/v1/inventory/" + productId + "/opening-stock", Map.of("quantity", new BigDecimal("50.000")));
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode openedBody = mapper.readTree(opened.getBody()).path("data");
        assertThat(openedBody.path("quantity").decimalValue()).isEqualByComparingTo("50");
        assertThat(openedBody.path("status").asText()).isEqualTo("IN_STOCK");
        assertThat(openedBody.path("openingRecorded").asBoolean()).isTrue();

        JsonNode movements = mapper.readTree(
                        get(owner.token(), "/api/v1/inventory/" + productId + "/movements").getBody())
                .path("data")
                .path("items");
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).path("type").asText()).isEqualTo("OPENING_STOCK");
        assertThat(movements.get(0).path("quantityBefore").decimalValue()).isEqualByComparingTo("0");
        assertThat(movements.get(0).path("quantityAfter").decimalValue()).isEqualByComparingTo("50");

        ResponseEntity<String> duplicateOpen = post(
                owner.token(), "/api/v1/inventory/" + productId + "/opening-stock", Map.of("quantity", new BigDecimal("10")));
        assertThat(duplicateOpen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        patch(owner.token(), "/api/v1/inventory/" + productId + "/reorder-level", Map.of("reorderLevel", new BigDecimal("10")));

        ResponseEntity<String> out = post(
                owner.token(),
                "/api/v1/inventory/" + productId + "/adjustments",
                adjustment("ADJUSTMENT_OUT", "3", "DAMAGED", "Torn packs"));
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode afterOut = mapper.readTree(out.getBody()).path("data");
        assertThat(afterOut.path("quantity").decimalValue()).isEqualByComparingTo("47");
        assertThat(afterOut.path("status").asText()).isEqualTo("IN_STOCK");

        ResponseEntity<String> in = post(
                owner.token(),
                "/api/v1/inventory/" + productId + "/adjustments",
                adjustment("ADJUSTMENT_IN", "2", "PHYSICAL_COUNT", null));
        assertThat(in.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(mapper.readTree(in.getBody()).path("data").path("quantity").decimalValue())
                .isEqualByComparingTo("49");

        ResponseEntity<String> tooMuch = post(
                owner.token(),
                "/api/v1/inventory/" + productId + "/adjustments",
                adjustment("ADJUSTMENT_OUT", "80", "MISSING", null));
        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapper.readTree(tooMuch.getBody()).path("error").path("message").asText())
                .contains("Available quantity: 49");

        ResponseEntity<String> zero = post(
                owner.token(),
                "/api/v1/inventory/" + productId + "/adjustments",
                adjustment("ADJUSTMENT_IN", "0", "OTHER", null));
        assertThat(zero.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> negativeReorder = patch(
                owner.token(), "/api/v1/inventory/" + productId + "/reorder-level", Map.of("reorderLevel", new BigDecimal("-1")));
        assertThat(negativeReorder.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> low = post(
                owner.token(),
                "/api/v1/inventory/" + productId + "/adjustments",
                adjustment("ADJUSTMENT_OUT", "40", "PHYSICAL_COUNT", null));
        assertThat(mapper.readTree(low.getBody()).path("data").path("status").asText()).isEqualTo("LOW_STOCK");

        ResponseEntity<String> empty = post(
                owner.token(),
                "/api/v1/inventory/" + productId + "/adjustments",
                adjustment("ADJUSTMENT_OUT", "9", "MISSING", null));
        assertThat(mapper.readTree(empty.getBody()).path("data").path("status").asText()).isEqualTo("OUT_OF_STOCK");

        JsonNode history = mapper.readTree(
                        get(owner.token(), "/api/v1/inventory/" + productId + "/movements").getBody())
                .path("data")
                .path("items");
        assertThat(history.size()).isGreaterThanOrEqualTo(4);
        assertThat(history.get(0).path("reason").asText()).isEqualTo("MISSING");
        assertThat(history.findValuesAsText("notes")).contains("Torn packs");

        JsonNode listed = mapper.readTree(get(owner.token(), "/api/v1/inventory?q=salt&status=OUT_OF_STOCK").getBody());
        assertThat(listed.path("data").path("items")).hasSize(1);

        JsonNode summary = mapper.readTree(get(owner.token(), "/api/v1/inventory/summary").getBody()).path("data");
        assertThat(summary.path("totalProducts").asInt()).isEqualTo(1);
        assertThat(summary.path("outOfStock").asInt()).isEqualTo(1);
    }

    @Test
    void cashierCanViewButCannotAdjust() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Pillai Mart");
        String productId = createProduct(owner.token(), "Aavin Milk 500ml", "AAVIN-500");
        post(owner.token(), "/api/v1/inventory/" + productId + "/opening-stock", Map.of("quantity", new BigDecimal("10")));

        String cashierEmail = "cashier-" + UUID.randomUUID() + "@retailflow.test";
        UUID cashierId = AuthTestSupport.insertMembership(
                jdbcTemplate,
                owner.tenantId(),
                cashierEmail,
                passwordEncoder.encode("CashierPass9"),
                "Ravi Cashier",
                TenantRole.CASHIER.name());
        String cashierToken = jwtService.issueToken(
                cashierId, cashierEmail, UUID.fromString(owner.tenantId()), TenantRole.CASHIER);

        assertThat(get(cashierToken, "/api/v1/inventory/" + productId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post(
                        cashierToken,
                        "/api/v1/inventory/" + productId + "/adjustments",
                        adjustment("ADJUSTMENT_IN", "1", "OTHER", null))
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(cashierToken, "/api/v1/inventory/" + productId + "/opening-stock", Map.of("quantity", 1))
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patch(cashierToken, "/api/v1/inventory/" + productId + "/reorder-level", Map.of("reorderLevel", 2))
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String managerEmail = "manager-" + UUID.randomUUID() + "@retailflow.test";
        UUID managerId = AuthTestSupport.insertMembership(
                jdbcTemplate,
                owner.tenantId(),
                managerEmail,
                passwordEncoder.encode("ManagerPass9"),
                "Priya Manager",
                TenantRole.MANAGER.name());
        String managerToken = jwtService.issueToken(
                managerId, managerEmail, UUID.fromString(owner.tenantId()), TenantRole.MANAGER);
        assertThat(post(
                        managerToken,
                        "/api/v1/inventory/" + productId + "/adjustments",
                        adjustment("ADJUSTMENT_IN", "1", "PHYSICAL_COUNT", null))
                .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void tenantACannotAccessTenantBInventory() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Menon Store");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Shetty Store");
        String productB = createProduct(b.token(), "Hidden Rice", "HIDDEN-RICE");
        post(b.token(), "/api/v1/inventory/" + productB + "/opening-stock", Map.of("quantity", new BigDecimal("20")));

        assertThat(listSkus(a.token())).doesNotContain("HIDDEN-RICE");
        assertThat(get(a.token(), "/api/v1/inventory/" + productB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(a.token(), "/api/v1/inventory/" + productB + "/movements").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(
                        a.token(),
                        "/api/v1/inventory/" + productB + "/adjustments",
                        adjustment("ADJUSTMENT_IN", "1", "OTHER", null))
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(a.token(), "/api/v1/inventory/" + productB + "/opening-stock", Map.of("quantity", 5))
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(patch(a.token(), "/api/v1/inventory/" + productB + "/reorder-level", Map.of("reorderLevel", 1))
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        String productA = createProduct(a.token(), "Own Rice", "OWN-RICE");
        Map<String, Object> body = new HashMap<>();
        body.put("quantity", new BigDecimal("8"));
        body.put("tenantId", b.tenantId());
        assertThat(post(a.token(), "/api/v1/inventory/" + productA + "/opening-stock", body).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        JsonNode queried = mapper.readTree(get(a.token(), "/api/v1/inventory?tenantId=" + b.tenantId()).getBody());
        List<String> skus = queried.path("data").path("items").findValuesAsText("sku");
        assertThat(skus).contains("OWN-RICE").doesNotContain("HIDDEN-RICE");
        assertThat(get(a.token(), "/api/v1/inventory/" + b.tenantId()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        List<String> visible = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', 'off', true)");
                statement.execute("SELECT set_config('app.current_tenant_id', '" + a.tenantId() + "', true)");
                var rs = statement.executeQuery(
                        "SELECT p.sku FROM inventory_balances i JOIN products p ON p.id = i.product_id");
                java.util.ArrayList<String> found = new java.util.ArrayList<>();
                while (rs.next()) {
                    found.add(rs.getString(1));
                }
                connection.rollback();
                return found;
            }
        });
        assertThat(visible).contains("OWN-RICE").doesNotContain("HIDDEN-RICE");
    }

    @Test
    void concurrentAdjustmentsDoNotLoseUpdates() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Concurrent Kirana");
        String productId = createProduct(owner.token(), "Toor Dal 1kg", "TOOR-1");
        post(owner.token(), "/api/v1/inventory/" + productId + "/opening-stock", Map.of("quantity", new BigDecimal("100")));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<ResponseEntity<String>> plus = pool.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            return post(
                    owner.token(),
                    "/api/v1/inventory/" + productId + "/adjustments",
                    adjustment("ADJUSTMENT_IN", "10", "PHYSICAL_COUNT", "count plus"));
        });
        Future<ResponseEntity<String>> minus = pool.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            return post(
                    owner.token(),
                    "/api/v1/inventory/" + productId + "/adjustments",
                    adjustment("ADJUSTMENT_OUT", "20", "MISSING", "count minus"));
        });
        start.countDown();
        assertThat(plus.get(20, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(minus.get(20, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        pool.shutdownNow();

        JsonNode current = mapper.readTree(get(owner.token(), "/api/v1/inventory/" + productId).getBody()).path("data");
        assertThat(current.path("quantity").decimalValue()).isEqualByComparingTo("90");
        JsonNode history = mapper.readTree(
                        get(owner.token(), "/api/v1/inventory/" + productId + "/movements").getBody())
                .path("data")
                .path("items");
        assertThat(history).hasSize(3);
    }

    private String createProduct(String token, String name, String sku) throws Exception {
        String categoryId = mapper.readTree(post(token, "/api/v1/categories", Map.of("name", "Staples-" + UUID.randomUUID()))
                        .getBody())
                .path("data")
                .path("id")
                .asText();
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryId", categoryId);
        body.put("sku", sku);
        body.put("costPrice", new BigDecimal("10.00"));
        body.put("sellingPrice", new BigDecimal("12.00"));
        body.put("gstRate", new BigDecimal("5"));
        body.put("unit", "KG");
        ResponseEntity<String> created = post(token, "/api/v1/products", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(created.getBody()).path("data").path("id").asText();
    }

    private Map<String, Object> adjustment(String type, String qty, String reason, String notes) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("quantity", new BigDecimal(qty));
        body.put("reason", reason);
        if (notes != null) {
            body.put("notes", notes);
        }
        return body;
    }

    private List<String> listSkus(String token) throws Exception {
        JsonNode body = mapper.readTree(get(token, "/api/v1/inventory").getBody());
        return body.path("data").path("items").findValuesAsText("sku");
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
