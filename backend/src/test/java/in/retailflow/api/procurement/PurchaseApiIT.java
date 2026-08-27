package in.retailflow.api.procurement;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.retailflow.api.identity.domain.TenantRole;
import in.retailflow.api.security.jwt.JwtService;
import in.retailflow.api.support.AuthTestSupport;
import in.retailflow.api.support.AuthTestSupport.SignupResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PurchaseApiIT {

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
    void draftDoesNotChangeInventoryThenReceiveIncreasesStockAndCreatesMovement() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Purchase Kirana");
        String token = owner.token();
        String milk = createProduct(token, "Aavin Milk 500ml", "AAVIN-M4-500", "5", "ML", "25.00");
        String cola = createProduct(token, "Coca Cola 750ml", "COLA-M4-750", "28", "ML", "35.00");
        post(token, "/api/v1/inventory/" + milk + "/opening-stock", Map.of("quantity", new BigDecimal("20.000")));
        String supplierId = createSupplier(token, "ABC Distributors");

        ResponseEntity<String> created = post(token, "/api/v1/purchases", purchaseBody(supplierId, milk, cola));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode purchase = mapper.readTree(created.getBody()).path("data");
        String purchaseId = purchase.path("id").asText();
        assertThat(purchase.path("purchaseNumber").asText()).isEqualTo("PUR-000001");
        assertThat(purchase.path("status").asText()).isEqualTo("DRAFT");
        assertThat(purchase.path("subtotal").decimalValue()).isEqualByComparingTo("4250.00");
        assertThat(purchase.path("taxAmount").decimalValue()).isEqualByComparingTo("615.00");
        assertThat(purchase.path("totalAmount").decimalValue()).isEqualByComparingTo("4865.00");
        assertThat(purchase.path("items")).hasSize(2);

        assertThat(stock(token, milk)).isEqualByComparingTo("20");
        assertThat(stock(token, cola)).isEqualByComparingTo("0");

        ResponseEntity<String> received = post(token, "/api/v1/purchases/" + purchaseId + "/receive", Map.of());
        assertThat(received.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode after = mapper.readTree(received.getBody()).path("data");
        assertThat(after.path("status").asText()).isEqualTo("RECEIVED");
        assertThat(after.path("receivedAt").asText()).isNotBlank();

        assertThat(stock(token, milk)).isEqualByComparingTo("120");
        assertThat(stock(token, cola)).isEqualByComparingTo("50");

        JsonNode movements = mapper.readTree(get(token, "/api/v1/inventory/" + milk + "/movements").getBody())
                .path("data")
                .path("items");
        JsonNode receipt = null;
        for (JsonNode row : movements) {
            if ("PURCHASE_RECEIPT".equals(row.path("type").asText())) {
                receipt = row;
                break;
            }
        }
        assertThat(receipt).isNotNull();
        assertThat(receipt.path("quantityBefore").decimalValue()).isEqualByComparingTo("20");
        assertThat(receipt.path("quantity").decimalValue()).isEqualByComparingTo("100");
        assertThat(receipt.path("quantityAfter").decimalValue()).isEqualByComparingTo("120");
        assertThat(receipt.path("referenceType").asText()).isEqualTo("PURCHASE");
        assertThat(receipt.path("referenceId").asText()).isEqualTo(purchaseId);

        ResponseEntity<String> second = post(token, "/api/v1/purchases/" + purchaseId + "/receive", Map.of());
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stock(token, milk)).isEqualByComparingTo("120");

        JsonNode summary = mapper.readTree(get(token, "/api/v1/purchases/summary").getBody()).path("data");
        assertThat(summary.path("receivedPurchases").asInt()).isEqualTo(1);
        assertThat(summary.path("receivedValue").decimalValue()).isEqualByComparingTo("4865.00");
    }

    @Test
    void cannotEditOrCancelReceivedPurchaseAndDraftCancelLeavesStockUnchanged() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Cancel Purchase Shop");
        String token = owner.token();
        String milk = createProduct(token, "Milk Pack", "MILK-CAN-1", "5", "ML", "20.00");
        String supplierId = createSupplier(token, "Vendor One");

        JsonNode draft = mapper.readTree(post(
                        token,
                        "/api/v1/purchases",
                        Map.of(
                                "supplierId",
                                supplierId,
                                "purchaseDate",
                                LocalDate.now().toString(),
                                "items",
                                List.of(item(milk, "10", "20", "5"))))
                .getBody())
                .path("data");
        String draftId = draft.path("id").asText();
        assertThat(stock(token, milk)).isEqualByComparingTo("0");

        Map<String, Object> edited = Map.of(
                "supplierId",
                supplierId,
                "purchaseDate",
                LocalDate.now().toString(),
                "items",
                List.of(item(milk, "4", "20", "5")));
        assertThat(put(token, "/api/v1/purchases/" + draftId, edited).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stock(token, milk)).isEqualByComparingTo("0");

        assertThat(post(token, "/api/v1/purchases/" + draftId + "/cancel", Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(stock(token, milk)).isEqualByComparingTo("0");
        assertThat(mapper.readTree(get(token, "/api/v1/purchases/" + draftId).getBody())
                        .path("data")
                        .path("status")
                        .asText())
                .isEqualTo("CANCELLED");

        JsonNode live = mapper.readTree(post(
                        token,
                        "/api/v1/purchases",
                        Map.of(
                                "supplierId",
                                supplierId,
                                "purchaseDate",
                                LocalDate.now().toString(),
                                "items",
                                List.of(item(milk, "5", "20", "5"))))
                .getBody())
                .path("data");
        String liveId = live.path("id").asText();
        assertThat(post(token, "/api/v1/purchases/" + liveId + "/receive", Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(stock(token, milk)).isEqualByComparingTo("5");
        assertThat(put(token, "/api/v1/purchases/" + liveId, edited).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post(token, "/api/v1/purchases/" + liveId + "/cancel", Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(stock(token, milk)).isEqualByComparingTo("5");
    }

    @Test
    void cashierCannotMutateAndForeignProductOrSupplierIsRejected() throws Exception {
        SignupResult ownerA = AuthTestSupport.signup(rest, mapper, "Purchase Shop A");
        SignupResult ownerB = AuthTestSupport.signup(rest, mapper, "Purchase Shop B");
        String productB = createProduct(ownerB.token(), "Foreign SKU", "FOR-B-1", "5", "PCS", "10.00");
        String supplierB = createSupplier(ownerB.token(), "Foreign Vendor");
        String supplierA = createSupplier(ownerA.token(), "Local Vendor");
        String productA = createProduct(ownerA.token(), "Local SKU", "LOC-A-1", "5", "PCS", "10.00");

        assertThat(post(
                        ownerA.token(),
                        "/api/v1/purchases",
                        Map.of(
                                "supplierId",
                                supplierA,
                                "purchaseDate",
                                LocalDate.now().toString(),
                                "items",
                                List.of(item(productB, "1", "10", "5"))))
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(
                        ownerA.token(),
                        "/api/v1/purchases",
                        Map.of(
                                "supplierId",
                                supplierB,
                                "purchaseDate",
                                LocalDate.now().toString(),
                                "items",
                                List.of(item(productA, "1", "10", "5"))))
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode purchaseB = mapper.readTree(post(
                        ownerB.token(),
                        "/api/v1/purchases",
                        Map.of(
                                "supplierId",
                                supplierB,
                                "purchaseDate",
                                LocalDate.now().toString(),
                                "items",
                                List.of(item(productB, "2", "10", "5"))))
                .getBody())
                .path("data");
        String purchaseBId = purchaseB.path("id").asText();
        assertThat(get(ownerA.token(), "/api/v1/purchases/" + purchaseBId).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(ownerA.token(), "/api/v1/purchases/" + purchaseBId + "/receive", Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> injected = new HashMap<>();
        injected.put("supplierId", supplierA);
        injected.put("purchaseDate", LocalDate.now().toString());
        injected.put("tenantId", ownerB.tenantId());
        injected.put("items", List.of(item(productA, "1", "10", "5")));
        JsonNode created = mapper.readTree(post(ownerA.token(), "/api/v1/purchases", injected).getBody()).path("data");
        assertThat(created.path("id").asText()).isNotBlank();
        UUID storedTenant = jdbcTemplate.execute((ConnectionCallback<UUID>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', 'on', true)");
                try (var rs = statement.executeQuery(
                        "SELECT tenant_id FROM purchases WHERE id = '" + created.path("id").asText() + "'")) {
                    rs.next();
                    UUID tenantId = rs.getObject(1, UUID.class);
                    connection.rollback();
                    return tenantId;
                }
            }
        });
        assertThat(storedTenant).isEqualTo(UUID.fromString(ownerA.tenantId()));

        String cashier = cashierToken(ownerA);
        assertThat(post(
                        cashier,
                        "/api/v1/purchases",
                        Map.of(
                                "supplierId",
                                supplierA,
                                "purchaseDate",
                                LocalDate.now().toString(),
                                "items",
                                List.of(item(productA, "1", "10", "5"))))
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(cashier, "/api/v1/purchases/" + created.path("id").asText() + "/receive", Map.of())
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(cashier, "/api/v1/purchases").getStatusCode()).isEqualTo(HttpStatus.OK);

        String manager = managerToken(ownerA);
        assertThat(post(manager, "/api/v1/purchases/" + created.path("id").asText() + "/receive", Map.of())
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        List<String> visible = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', 'off', true)");
                statement.execute("SELECT set_config('app.current_tenant_id', '" + ownerA.tenantId() + "', true)");
                var rs = statement.executeQuery("SELECT id::text FROM purchases");
                java.util.ArrayList<String> found = new java.util.ArrayList<>();
                while (rs.next()) {
                    found.add(rs.getString(1));
                }
                connection.rollback();
                return found;
            }
        });
        assertThat(visible).contains(created.path("id").asText());
        assertThat(visible).doesNotContain(purchaseBId);
    }

    @Test
    void concurrentReceiveDoesNotDoubleStock() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Concurrent Receive Shop");
        String token = owner.token();
        String milk = createProduct(token, "Concurrent Milk", "CON-MILK", "5", "ML", "20.00");
        post(token, "/api/v1/inventory/" + milk + "/opening-stock", Map.of("quantity", new BigDecimal("20.000")));
        String supplierId = createSupplier(token, "Concurrent Vendor");
        JsonNode purchase = mapper.readTree(post(
                        token,
                        "/api/v1/purchases",
                        Map.of(
                                "supplierId",
                                supplierId,
                                "purchaseDate",
                                LocalDate.now().toString(),
                                "items",
                                List.of(item(milk, "100", "25", "5"))))
                .getBody())
                .path("data");
        String purchaseId = purchase.path("id").asText();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        Future<ResponseEntity<String>> first = pool.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            ResponseEntity<String> response = post(token, "/api/v1/purchases/" + purchaseId + "/receive", Map.of());
            if (response.getStatusCode() == HttpStatus.OK) {
                successes.incrementAndGet();
            }
            return response;
        });
        Future<ResponseEntity<String>> second = pool.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            ResponseEntity<String> response = post(token, "/api/v1/purchases/" + purchaseId + "/receive", Map.of());
            if (response.getStatusCode() == HttpStatus.OK) {
                successes.incrementAndGet();
            }
            return response;
        });
        start.countDown();
        HttpStatusCode statusA = first.get(20, TimeUnit.SECONDS).getStatusCode();
        HttpStatusCode statusB = second.get(20, TimeUnit.SECONDS).getStatusCode();
        pool.shutdownNow();
        assertThat(List.of(statusA, statusB)).contains(HttpStatus.OK);
        assertThat(successes.get()).isEqualTo(1);
        assertThat(stock(token, milk)).isEqualByComparingTo("120");
    }

    @Test
    void inactiveSupplierAndProductRejectedOnCreate() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Inactive Purchase Shop");
        String token = owner.token();
        String supplierId = createSupplier(token, "Sleepy Vendor");
        String productId = createProduct(token, "Sleepy SKU", "SLP-1", "5", "PCS", "10.00");
        patch(token, "/api/v1/suppliers/" + supplierId + "/status", Map.of("active", false));
        assertThat(post(
                        token,
                        "/api/v1/purchases",
                        Map.of(
                                "supplierId",
                                supplierId,
                                "purchaseDate",
                                LocalDate.now().toString(),
                                "items",
                                List.of(item(productId, "1", "10", "5"))))
                .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        String activeSupplier = createSupplier(token, "Awake Vendor");
        patch(token, "/api/v1/products/" + productId + "/status", Map.of("active", false));
        assertThat(post(
                        token,
                        "/api/v1/purchases",
                        Map.of(
                                "supplierId",
                                activeSupplier,
                                "purchaseDate",
                                LocalDate.now().toString(),
                                "items",
                                List.of(item(productId, "1", "10", "5"))))
                .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private String createProduct(String token, String name, String sku, String gst, String unit, String cost)
            throws Exception {
        String categoryId = mapper.readTree(post(token, "/api/v1/categories", Map.of("name", "Cat-" + UUID.randomUUID()))
                        .getBody())
                .path("data")
                .path("id")
                .asText();
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryId", categoryId);
        body.put("sku", sku);
        body.put("costPrice", new BigDecimal(cost));
        body.put("sellingPrice", new BigDecimal(cost).add(new BigDecimal("10")));
        body.put("gstRate", new BigDecimal(gst));
        body.put("unit", unit);
        ResponseEntity<String> created = post(token, "/api/v1/products", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(created.getBody()).path("data").path("id").asText();
    }

    private String createSupplier(String token, String name) throws Exception {
        ResponseEntity<String> created = post(token, "/api/v1/suppliers", Map.of("name", name));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(created.getBody()).path("data").path("id").asText();
    }

    private Map<String, Object> purchaseBody(String supplierId, String milk, String cola) {
        return Map.of(
                "supplierId",
                supplierId,
                "purchaseDate",
                LocalDate.now().toString(),
                "items",
                List.of(item(milk, "100", "25", "5"), item(cola, "50", "35", "28")));
    }

    private Map<String, Object> item(String productId, String qty, String cost, String gst) {
        return Map.of("productId", productId, "quantity", new BigDecimal(qty), "unitCost", new BigDecimal(cost), "gstRate", new BigDecimal(gst));
    }

    private BigDecimal stock(String token, String productId) throws Exception {
        return mapper.readTree(get(token, "/api/v1/inventory/" + productId).getBody())
                .path("data")
                .path("quantity")
                .decimalValue();
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
