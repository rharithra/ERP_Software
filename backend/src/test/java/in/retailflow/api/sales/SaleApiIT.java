package in.retailflow.api.sales;

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
class SaleApiIT {

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
    void draftDoesNotChangeStockThenCompleteDecreasesInventoryAndCreatesInvoice() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Sale Kirana");
        String token = owner.token();
        String cola = createProduct(token, "Coca Cola 750ml", "COLA-M5-750", "8901234567890", "28", "ML", "40.00");
        post(token, "/api/v1/inventory/" + cola + "/opening-stock", Map.of("quantity", new BigDecimal("100.000")));
        String customerId = createCustomer(token, "Walk Regular", "9888800001");

        Map<String, Object> body = saleBody(customerId, cola, "3", "40.00", "28", "0");
        ResponseEntity<String> created = post(token, "/api/v1/sales", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode sale = mapper.readTree(created.getBody()).path("data");
        String saleId = sale.path("id").asText();
        assertThat(sale.path("saleNumber").asText()).isEqualTo("SAL-000001");
        assertThat(sale.path("status").asText()).isEqualTo("DRAFT");
        assertThat(sale.path("subtotal").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(sale.path("taxTotal").decimalValue()).isEqualByComparingTo("33.60");
        assertThat(sale.path("grandTotal").decimalValue()).isEqualByComparingTo("153.60");
        assertThat(sale.path("items")).hasSize(1);
        assertThat(sale.path("items").get(0).path("productName").asText()).isEqualTo("Coca Cola 750ml");
        assertThat(stock(token, cola)).isEqualByComparingTo("100");

        ResponseEntity<String> completed =
                post(token, "/api/v1/sales/" + saleId + "/complete", Map.of("paymentMethod", "CASH"));
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode after = mapper.readTree(completed.getBody()).path("data");
        assertThat(after.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(after.path("invoiceNumber").asText()).isEqualTo("INV-000001");
        assertThat(after.path("paymentMethod").asText()).isEqualTo("CASH");
        assertThat(after.path("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(stock(token, cola)).isEqualByComparingTo("97");

        JsonNode movements = mapper.readTree(get(token, "/api/v1/inventory/" + cola + "/movements").getBody())
                .path("data")
                .path("items");
        JsonNode saleMovement = null;
        for (JsonNode row : movements) {
            if ("SALE".equals(row.path("type").asText())) {
                saleMovement = row;
                break;
            }
        }
        assertThat(saleMovement).isNotNull();
        assertThat(saleMovement.path("quantityBefore").decimalValue()).isEqualByComparingTo("100");
        assertThat(saleMovement.path("quantity").decimalValue()).isEqualByComparingTo("3");
        assertThat(saleMovement.path("quantityAfter").decimalValue()).isEqualByComparingTo("97");
        assertThat(saleMovement.path("referenceType").asText()).isEqualTo("SALE");
        assertThat(saleMovement.path("referenceId").asText()).isEqualTo(saleId);

        JsonNode invoice = mapper.readTree(get(token, "/api/v1/sales/" + saleId + "/invoice").getBody()).path("data");
        assertThat(invoice.path("sale").path("invoiceNumber").asText()).isEqualTo("INV-000001");
        assertThat(invoice.path("sale").path("items").get(0).path("sku").asText()).isEqualTo("COLA-M5-750");
        assertThat(invoice.path("sale").path("grandTotal").decimalValue()).isEqualByComparingTo("153.60");
        assertThat(invoice.path("companyName").asText()).isEqualTo("Sale Kirana");

        ResponseEntity<String> second =
                post(token, "/api/v1/sales/" + saleId + "/complete", Map.of("paymentMethod", "UPI"));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stock(token, cola)).isEqualByComparingTo("97");
        assertThat(put(token, "/api/v1/sales/" + saleId, body).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelledSaleCannotBeCompletedAndDraftCancelLeavesStockUnchanged() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Cancel Sale Shop");
        String token = owner.token();
        String milk = createProduct(token, "Milk Pack", "MILK-SAL-1", null, "5", "ML", "30.00");
        post(token, "/api/v1/inventory/" + milk + "/opening-stock", Map.of("quantity", new BigDecimal("10.000")));

        JsonNode draft = mapper.readTree(post(token, "/api/v1/sales", saleBody(null, milk, "2", "30.00", "5", "0"))
                        .getBody())
                .path("data");
        String draftId = draft.path("id").asText();
        assertThat(stock(token, milk)).isEqualByComparingTo("10");
        assertThat(post(token, "/api/v1/sales/" + draftId + "/cancel", Map.of()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stock(token, milk)).isEqualByComparingTo("10");
        assertThat(post(token, "/api/v1/sales/" + draftId + "/complete", Map.of("paymentMethod", "CASH"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(get(token, "/api/v1/sales/" + draftId + "/invoice").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void insufficientStockRejectsEntireSale() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Short Stock Shop");
        String token = owner.token();
        String sku = createProduct(token, "Short Item", "SHORT-1", null, "5", "PCS", "10.00");
        post(token, "/api/v1/inventory/" + sku + "/opening-stock", Map.of("quantity", new BigDecimal("2.000")));
        JsonNode sale = mapper.readTree(post(token, "/api/v1/sales", saleBody(null, sku, "5", "10.00", "5", "0"))
                        .getBody())
                .path("data");
        ResponseEntity<String> complete =
                post(token, "/api/v1/sales/" + sale.path("id").asText() + "/complete", Map.of("paymentMethod", "UPI"));
        assertThat(complete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapper.readTree(complete.getBody()).path("error").path("code").asText())
                .isEqualTo("INSUFFICIENT_STOCK");
        assertThat(stock(token, sku)).isEqualByComparingTo("2");
        assertThat(mapper.readTree(get(token, "/api/v1/sales/" + sale.path("id").asText()).getBody())
                        .path("data")
                        .path("status")
                        .asText())
                .isEqualTo("DRAFT");
    }

    @Test
    void saleLevelDiscountAndMultipleItemsCalculateTotals() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Discount Shop");
        String token = owner.token();
        String milk = createProduct(token, "Milk", "DISC-M", null, "5", "ML", "20.00");
        String rice = createProduct(token, "Rice", "DISC-R", null, "5", "KG", "80.00");
        post(token, "/api/v1/inventory/" + milk + "/opening-stock", Map.of("quantity", new BigDecimal("50.000")));
        post(token, "/api/v1/inventory/" + rice + "/opening-stock", Map.of("quantity", new BigDecimal("50.000")));
        Map<String, Object> body = new HashMap<>();
        body.put("saleDate", LocalDate.now().toString());
        body.put("discount", new BigDecimal("50.00"));
        body.put("items", List.of(item(milk, "10", "20.00", "5"), item(rice, "5", "80.00", "5")));
        JsonNode sale = mapper.readTree(post(token, "/api/v1/sales", body).getBody()).path("data");
        assertThat(sale.path("subtotal").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(sale.path("discount").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(sale.path("taxTotal").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(sale.path("grandTotal").decimalValue()).isEqualByComparingTo("580.00");
    }

    @Test
    void cashierCanSellButNotCreateCustomerAndForeignIdsAreRejected() throws Exception {
        SignupResult ownerA = AuthTestSupport.signup(rest, mapper, "Sale Shop A");
        SignupResult ownerB = AuthTestSupport.signup(rest, mapper, "Sale Shop B");
        String productB = createProduct(ownerB.token(), "Foreign SKU", "FOR-S-1", null, "5", "PCS", "10.00");
        String customerB = createCustomer(ownerB.token(), "Foreign Buyer", "9111100001");
        String productA = createProduct(ownerA.token(), "Local SKU", "LOC-S-1", null, "5", "PCS", "10.00");
        post(ownerA.token(), "/api/v1/inventory/" + productA + "/opening-stock", Map.of("quantity", new BigDecimal("20.000")));
        String customerA = createCustomer(ownerA.token(), "Local Buyer", "9222200001");

        assertThat(post(ownerA.token(), "/api/v1/sales", saleBody(customerB, productA, "1", "10.00", "5", "0"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(ownerA.token(), "/api/v1/sales", saleBody(customerA, productB, "1", "10.00", "5", "0"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode saleB = mapper.readTree(post(ownerB.token(), "/api/v1/sales", saleBody(customerB, productB, "1", "10.00", "5", "0"))
                        .getBody())
                .path("data");
        assertThat(get(ownerA.token(), "/api/v1/sales/" + saleB.path("id").asText()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> injected = new HashMap<>(saleBody(customerA, productA, "1", "10.00", "5", "0"));
        injected.put("tenantId", ownerB.tenantId());
        JsonNode created = mapper.readTree(post(ownerA.token(), "/api/v1/sales", injected).getBody()).path("data");
        UUID storedTenant = jdbcTemplate.execute((ConnectionCallback<UUID>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', 'on', true)");
                try (var rs = statement.executeQuery(
                        "SELECT tenant_id FROM sales WHERE id = '" + created.path("id").asText() + "'")) {
                    rs.next();
                    UUID tenantId = rs.getObject(1, UUID.class);
                    connection.rollback();
                    return tenantId;
                }
            }
        });
        assertThat(storedTenant).isEqualTo(UUID.fromString(ownerA.tenantId()));

        String cashier = cashierToken(ownerA);
        assertThat(post(cashier, "/api/v1/customers", Map.of("name", "Should Fail")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode cashierSale = mapper.readTree(post(cashier, "/api/v1/sales", saleBody(customerA, productA, "2", "10.00", "5", "0"))
                        .getBody())
                .path("data");
        assertThat(post(
                        cashier,
                        "/api/v1/sales/" + cashierSale.path("id").asText() + "/complete",
                        Map.of("paymentMethod", "UPI"))
                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get(cashier, "/api/v1/sales/" + cashierSale.path("id").asText() + "/invoice").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(stock(ownerA.token(), productA)).isEqualByComparingTo("18");

        String manager = managerToken(ownerA);
        JsonNode managerSale = mapper.readTree(post(manager, "/api/v1/sales", saleBody(null, productA, "1", "10.00", "5", "0"))
                        .getBody())
                .path("data");
        assertThat(post(
                        manager,
                        "/api/v1/sales/" + managerSale.path("id").asText() + "/complete",
                        Map.of("paymentMethod", "CARD"))
                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void concurrentSalesCannotDriveStockNegative() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Concurrent Sale Shop");
        String token = owner.token();
        String sku = createProduct(token, "Limited Pack", "CON-SALE", null, "5", "PCS", "10.00");
        post(token, "/api/v1/inventory/" + sku + "/opening-stock", Map.of("quantity", new BigDecimal("10.000")));
        String saleA = mapper.readTree(post(token, "/api/v1/sales", saleBody(null, sku, "7", "10.00", "5", "0"))
                        .getBody())
                .path("data")
                .path("id")
                .asText();
        String saleB = mapper.readTree(post(token, "/api/v1/sales", saleBody(null, sku, "5", "10.00", "5", "0"))
                        .getBody())
                .path("data")
                .path("id")
                .asText();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        Future<ResponseEntity<String>> first = pool.submit(() -> completeConcurrent(token, saleA, start, successes, insufficient));
        Future<ResponseEntity<String>> second = pool.submit(() -> completeConcurrent(token, saleB, start, successes, insufficient));
        start.countDown();
        HttpStatusCode statusA = first.get(20, TimeUnit.SECONDS).getStatusCode();
        HttpStatusCode statusB = second.get(20, TimeUnit.SECONDS).getStatusCode();
        pool.shutdownNow();
        assertThat(List.of(statusA, statusB)).contains(HttpStatus.OK);
        assertThat(successes.get()).isEqualTo(1);
        assertThat(insufficient.get()).isEqualTo(1);
        BigDecimal remaining = stock(token, sku);
        assertThat(remaining).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(remaining.compareTo(new BigDecimal("3")) == 0 || remaining.compareTo(new BigDecimal("5")) == 0)
                .isTrue();
    }

    private ResponseEntity<String> completeConcurrent(
            String token,
            String saleId,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger insufficient)
            throws Exception {
        start.await(5, TimeUnit.SECONDS);
        ResponseEntity<String> response = post(token, "/api/v1/sales/" + saleId + "/complete", Map.of("paymentMethod", "CASH"));
        if (response.getStatusCode() == HttpStatus.OK) {
            successes.incrementAndGet();
        } else if (response.getStatusCode() == HttpStatus.CONFLICT
                && mapper.readTree(response.getBody()).path("error").path("code").asText().equals("INSUFFICIENT_STOCK")) {
            insufficient.incrementAndGet();
        }
        return response;
    }

    private String createProduct(
            String token, String name, String sku, String barcode, String gst, String unit, String selling)
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
        if (barcode != null) {
            body.put("barcode", barcode);
        }
        body.put("costPrice", new BigDecimal("10.00"));
        body.put("sellingPrice", new BigDecimal(selling));
        body.put("gstRate", new BigDecimal(gst));
        body.put("unit", unit);
        ResponseEntity<String> created = post(token, "/api/v1/products", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(created.getBody()).path("data").path("id").asText();
    }

    private String createCustomer(String token, String name, String phone) throws Exception {
        ResponseEntity<String> created = post(token, "/api/v1/customers", Map.of("name", name, "phone", phone));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(created.getBody()).path("data").path("id").asText();
    }

    private Map<String, Object> saleBody(
            String customerId, String productId, String qty, String price, String gst, String discount) {
        Map<String, Object> body = new HashMap<>();
        if (customerId != null) {
            body.put("customerId", customerId);
        }
        body.put("saleDate", LocalDate.now().toString());
        body.put("discount", new BigDecimal(discount));
        body.put("items", List.of(item(productId, qty, price, gst)));
        return body;
    }

    private Map<String, Object> item(String productId, String qty, String price, String gst) {
        return Map.of(
                "productId",
                productId,
                "quantity",
                new BigDecimal(qty),
                "unitPrice",
                new BigDecimal(price),
                "gstRate",
                new BigDecimal(gst));
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
}
