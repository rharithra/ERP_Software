package in.retailflow.api.pipeline;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PipelineApiIT {

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
    void pipelineFeedsExistingSaleEngineWithRealPaymentsAndDoesNotTouchStockUntilComplete() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Aqua Dealers", "APPLIANCES_WATER_PURIFIER", "PIPELINE");
        String token = owner.token();
        String product = createProduct(token, "Kent Grand", "KENT-GRAND", "18", "PCS", "50000.00");
        post(token, "/api/v1/inventory/" + product + "/opening-stock", Map.of("quantity", new BigDecimal("10.000")));

        Map<String, Object> leadBody = new HashMap<>();
        leadBody.put("name", "Ravi Kumar");
        leadBody.put("phone", "9876500001");
        leadBody.put("source", "WALK_IN");
        leadBody.put("requirement", "Water purifier");
        leadBody.put("expectedValue", new BigDecimal("50000.00"));
        leadBody.put("priority", "HIGH");
        JsonNode lead = mapper.readTree(post(token, "/api/v1/leads", leadBody).getBody()).path("data");
        assertThat(lead.path("leadNumber").asText()).isEqualTo("LEAD-000001");
        assertThat(lead.path("status").asText()).isEqualTo("NEW");
        String leadId = lead.path("id").asText();

        Map<String, Object> follow = new HashMap<>();
        follow.put("leadId", leadId);
        follow.put("type", "CALL");
        follow.put("dueDate", LocalDate.now().toString());
        follow.put("notes", "Discuss water purifier quotation");
        JsonNode followUp = mapper.readTree(post(token, "/api/v1/follow-ups", follow).getBody()).path("data");
        assertThat(post(token, "/api/v1/follow-ups/" + followUp.path("id").asText() + "/complete", Map.of("outcome", "Interested"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode customer = mapper.readTree(post(token, "/api/v1/leads/" + leadId + "/convert-customer", Map.of())
                        .getBody())
                .path("data");
        String customerId = customer.path("id").asText();
        assertThat(customer.path("name").asText()).isEqualTo("Ravi Kumar");

        Map<String, Object> quotation = new HashMap<>();
        quotation.put("leadId", leadId);
        quotation.put("customerId", customerId);
        quotation.put("quotationDate", LocalDate.now().toString());
        quotation.put("validUntil", LocalDate.now().plusDays(10).toString());
        quotation.put("discount", new BigDecimal("0.00"));
        quotation.put("items", List.of(line(product, "1", "42372.88", "18")));
        JsonNode qt = mapper.readTree(post(token, "/api/v1/quotations", quotation).getBody()).path("data");
        assertThat(qt.path("quotationNumber").asText()).isEqualTo("QT-000001");
        assertThat(qt.path("items").get(0).path("gstRate").decimalValue()).isEqualByComparingTo("18");
        assertThat(qt.path("items").get(0).path("productName").asText()).isEqualTo("Kent Grand");
        String quotationId = qt.path("id").asText();
        assertThat(post(token, "/api/v1/quotations/" + quotationId + "/send", Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post(token, "/api/v1/quotations/" + quotationId + "/accept", Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode order = mapper.readTree(post(token, "/api/v1/sales-orders/from-quotation/" + quotationId, Map.of())
                        .getBody())
                .path("data");
        assertThat(order.path("orderNumber").asText()).isEqualTo("SO-000001");
        assertThat(order.path("grandTotal").decimalValue()).isEqualByComparingTo(qt.path("grandTotal").decimalValue());
        String orderId = order.path("id").asText();
        assertThat(post(token, "/api/v1/sales-orders/" + orderId + "/confirm", Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(stock(token, product)).isEqualByComparingTo("10");

        BigDecimal total = mapper.readTree(get(token, "/api/v1/sales-orders/" + orderId).getBody())
                .path("data")
                .path("grandTotal")
                .decimalValue();
        Map<String, Object> advance = new HashMap<>();
        advance.put("salesOrderId", orderId);
        advance.put("amount", new BigDecimal("10000.00"));
        advance.put("paymentMethod", "UPI");
        advance.put("paymentDate", LocalDate.now().toString());
        JsonNode pay1 = mapper.readTree(post(token, "/api/v1/payments", advance).getBody()).path("data");
        assertThat(pay1.path("paymentNumber").asText()).isEqualTo("PAY-000001");
        JsonNode afterAdvance = mapper.readTree(get(token, "/api/v1/sales-orders/" + orderId).getBody()).path("data");
        assertThat(afterAdvance.path("advancePaid").decimalValue()).isEqualByComparingTo("10000.00");
        assertThat(afterAdvance.path("outstandingAmount").decimalValue())
                .isEqualByComparingTo(total.subtract(new BigDecimal("10000.00")));
        assertThat(afterAdvance.path("paymentStatus").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(stock(token, product)).isEqualByComparingTo("10");

        Map<String, Object> tooMuch = new HashMap<>(advance);
        tooMuch.put("amount", total);
        ResponseEntity<String> overflow = post(token, "/api/v1/payments", tooMuch);
        assertThat(overflow.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.readTree(overflow.getBody()).path("error").path("code").asText())
                .isEqualTo("PAYMENT_EXCEEDS_OUTSTANDING");

        JsonNode sale = mapper.readTree(post(token, "/api/v1/sales-orders/" + orderId + "/convert-sale", Map.of())
                        .getBody())
                .path("data");
        assertThat(sale.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(sale.path("invoiceNumber").asText()).isEqualTo("INV-000001");
        assertThat(sale.path("paidAmount").decimalValue()).isEqualByComparingTo("10000.00");
        assertThat(stock(token, product)).isEqualByComparingTo("9");

        String saleId = sale.path("id").asText();
        Map<String, Object> second = new HashMap<>();
        second.put("saleId", saleId);
        second.put("amount", new BigDecimal("30000.00"));
        second.put("paymentMethod", "CASH");
        second.put("paymentDate", LocalDate.now().toString());
        assertThat(post(token, "/api/v1/payments", second).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode afterSecond = mapper.readTree(get(token, "/api/v1/sales/" + saleId).getBody()).path("data");
        assertThat(afterSecond.path("paidAmount").decimalValue()).isEqualByComparingTo("40000.00");

        BigDecimal remaining = afterSecond.path("outstandingAmount").decimalValue();
        Map<String, Object> finalPay = new HashMap<>();
        finalPay.put("saleId", saleId);
        finalPay.put("amount", remaining);
        finalPay.put("paymentMethod", "CARD");
        finalPay.put("paymentDate", LocalDate.now().toString());
        assertThat(post(token, "/api/v1/payments", finalPay).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode paid = mapper.readTree(get(token, "/api/v1/sales/" + saleId).getBody()).path("data");
        assertThat(paid.path("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(paid.path("outstandingAmount").decimalValue()).isEqualByComparingTo("0");

        JsonNode timeline = mapper.readTree(get(token, "/api/v1/leads/" + leadId + "/activities").getBody()).path("data");
        assertThat(timeline.toString()).contains("LEAD_CREATED");
        assertThat(timeline.toString()).contains("PAYMENT_RECEIVED");
        assertThat(timeline.toString()).contains("SALE_CREATED");

        JsonNode dashboard = mapper.readTree(get(token, "/api/v1/pipeline/dashboard").getBody()).path("data");
        assertThat(dashboard.path("wonThisMonth").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode invoice = mapper.readTree(get(token, "/api/v1/sales/" + saleId + "/invoice").getBody()).path("data");
        assertThat(invoice.path("sale").path("invoiceNumber").asText()).isEqualTo("INV-000001");
    }

    @Test
    void tenantIsolationAndCashierCannotManagePipeline() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Pipe A", "FURNITURE", "PIPELINE");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Pipe B", "FURNITURE", "PIPELINE");
        JsonNode leadA = mapper.readTree(post(a.token(), "/api/v1/leads", Map.of("name", "Secret", "source", "PHONE"))
                        .getBody())
                .path("data");
        assertThat(get(b.token(), "/api/v1/leads/" + leadA.path("id").asText()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        String cashier = token(a, TenantRole.CASHIER, "Cash");
        assertThat(get(cashier, "/api/v1/leads").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(cashier, "/api/v1/leads", Map.of("name", "No", "source", "WALK_IN")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        Map<String, Object> hijack = new HashMap<>();
        hijack.put("name", "Hijack");
        hijack.put("source", "WALK_IN");
        hijack.put("tenantId", b.tenantId());
        JsonNode created = mapper.readTree(post(a.token(), "/api/v1/leads", hijack).getBody()).path("data");
        assertThat(get(a.token(), "/api/v1/leads/" + created.path("id").asText()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get(b.token(), "/api/v1/leads/" + created.path("id").asText()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void quotationGstUsesProductSnapshotAndPosStillPaysInFull() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Hybrid Shop", "ELECTRONICS_COMPUTER", "HYBRID");
        String token = owner.token();
        String sku = createProduct(token, "Laptop", "LAP-1", "18", "PCS", "40000.00");
        post(token, "/api/v1/inventory/" + sku + "/opening-stock", Map.of("quantity", new BigDecimal("5.000")));
        String customerId = mapper.readTree(post(token, "/api/v1/customers", Map.of("name", "Arun", "phone", "9000011111"))
                        .getBody())
                .path("data")
                .path("id")
                .asText();
        Map<String, Object> quotation = new HashMap<>();
        quotation.put("customerId", customerId);
        quotation.put("quotationDate", LocalDate.now().toString());
        quotation.put("validUntil", LocalDate.now().plusDays(5).toString());
        quotation.put("items", List.of(line(sku, "1", "40000.00", "18")));
        JsonNode qt = mapper.readTree(post(token, "/api/v1/quotations", quotation).getBody()).path("data");
        assertThat(qt.path("taxTotal").decimalValue()).isEqualByComparingTo("7200.00");
        assertThat(qt.path("grandTotal").decimalValue()).isEqualByComparingTo("47200.00");

        Map<String, Object> pos = new HashMap<>();
        pos.put("saleDate", LocalDate.now().toString());
        pos.put("discount", new BigDecimal("0.00"));
        pos.put("items", List.of(line(sku, "1", "40000.00", "18")));
        JsonNode sale = mapper.readTree(post(token, "/api/v1/sales", pos).getBody()).path("data");
        JsonNode completed = mapper.readTree(post(
                                token,
                                "/api/v1/sales/" + sale.path("id").asText() + "/complete",
                                Map.of("paymentMethod", "CASH"))
                        .getBody())
                .path("data");
        assertThat(completed.path("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(completed.path("paidAmount").decimalValue()).isEqualByComparingTo(completed.path("grandTotal").decimalValue());
        assertThat(stock(token, sku)).isEqualByComparingTo("4");
    }

    private String createProduct(String token, String name, String sku, String gst, String unit, String selling)
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
        body.put("costPrice", new BigDecimal("10.00"));
        body.put("sellingPrice", new BigDecimal(selling));
        body.put("gstRate", new BigDecimal(gst));
        body.put("unit", unit);
        return mapper.readTree(post(token, "/api/v1/products", body).getBody()).path("data").path("id").asText();
    }

    private Map<String, Object> line(String productId, String qty, String price, String gst) {
        return Map.of(
                "productId", productId,
                "quantity", new BigDecimal(qty),
                "unitPrice", new BigDecimal(price),
                "gstRate", new BigDecimal(gst),
                "discount", new BigDecimal("0.00"));
    }

    private BigDecimal stock(String token, String productId) throws Exception {
        return mapper.readTree(get(token, "/api/v1/inventory/" + productId).getBody())
                .path("data")
                .path("quantity")
                .decimalValue();
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

    private ResponseEntity<String> post(String token, String path, Object body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, AuthTestSupport.bearer(token)), String.class);
    }
}
