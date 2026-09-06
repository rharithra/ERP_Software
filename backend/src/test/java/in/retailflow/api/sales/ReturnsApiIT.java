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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReturnsApiIT {

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
    void fullPaidReturnCreatesCreditThenRefundAndApplyCreditOnNextSale() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Return Shop");
        String token = owner.token();
        String product = createProduct(token, "Water Purifier", "WP-M8", "0", "PCS", "40000.00");
        post(token, "/api/v1/inventory/" + product + "/opening-stock", Map.of("quantity", new BigDecimal("10.000")));
        String customerId = createCustomer(token, "Ravi Kumar", "9888810001");

        JsonNode sale = completeSale(token, customerId, product, "1", "40000.00", "0", "40000.00", "0");
        String saleId = sale.path("id").asText();
        String saleItemId = sale.path("items").get(0).path("id").asText();
        assertThat(stock(token, product)).isEqualByComparingTo("9");
        assertThat(sale.path("paymentStatus").asText()).isEqualTo("PAID");

        JsonNode draft = mapper.readTree(post(
                        token,
                        "/api/v1/returns",
                        returnBody(saleId, saleItemId, "1", "DEFECTIVE"))
                .getBody())
                .path("data");
        assertThat(draft.path("returnNumber").asText()).isEqualTo("RET-000001");
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(stock(token, product)).isEqualByComparingTo("9");

        JsonNode completed = mapper.readTree(post(token, "/api/v1/returns/" + draft.path("id").asText() + "/complete", Map.of())
                        .getBody())
                .path("data");
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(stock(token, product)).isEqualByComparingTo("10");

        JsonNode afterReturn = mapper.readTree(get(token, "/api/v1/sales/" + saleId).getBody()).path("data");
        assertThat(afterReturn.path("netSaleAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(afterReturn.path("actualPaidAmount").decimalValue()).isEqualByComparingTo("40000.00");
        assertThat(afterReturn.path("outstandingAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(afterReturn.path("customerCreditAmount").decimalValue()).isEqualByComparingTo("40000.00");
        assertThat(afterReturn.path("paymentStatus").asText()).isEqualTo("REFUND_DUE");
        assertThat(afterReturn.path("returnStatus").asText()).isEqualTo("FULLY_RETURNED");
        assertThat(afterReturn.path("paidAmount").decimalValue()).isEqualByComparingTo("40000.00");

        ResponseEntity<String> retry = post(token, "/api/v1/returns/" + draft.path("id").asText() + "/complete", Map.of());
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stock(token, product)).isEqualByComparingTo("10");

        JsonNode refund = mapper.readTree(post(
                        token,
                        "/api/v1/refunds",
                        Map.of(
                                "saleId",
                                saleId,
                                "saleReturnId",
                                completed.path("id").asText(),
                                "amount",
                                new BigDecimal("5000.00"),
                                "paymentMethod",
                                "CASH"))
                .getBody())
                .path("data");
        assertThat(refund.path("refundNumber").asText()).isEqualTo("REF-000001");
        JsonNode refundDone = mapper.readTree(post(token, "/api/v1/refunds/" + refund.path("id").asText() + "/complete", Map.of())
                        .getBody())
                .path("data");
        assertThat(refundDone.path("status").asText()).isEqualTo("COMPLETED");

        JsonNode afterRefund = mapper.readTree(get(token, "/api/v1/sales/" + saleId).getBody()).path("data");
        assertThat(afterRefund.path("customerCreditAmount").decimalValue()).isEqualByComparingTo("35000.00");

        JsonNode credit = mapper.readTree(get(token, "/api/v1/customers/" + customerId + "/credit").getBody()).path("data");
        assertThat(credit.path("availableCredit").decimalValue()).isEqualByComparingTo("35000.00");

        JsonNode second = completeSale(token, customerId, product, "1", "20000.00", "5000.00", "10000.00", "0");
        assertThat(second.path("customerCreditApplied").decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(second.path("actualPaidAmount").decimalValue()).isEqualByComparingTo("10000.00");
        assertThat(second.path("outstandingAmount").decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(second.path("paymentStatus").asText()).isEqualTo("PARTIALLY_PAID");

        JsonNode financial = mapper.readTree(get(token, "/api/v1/customers/" + customerId + "/financial").getBody())
                .path("data");
        assertThat(financial.path("availableCredit").decimalValue()).isEqualByComparingTo("30000.00");
        assertThat(financial.path("outstanding").decimalValue()).isEqualByComparingTo("5000.00");
    }

    @Test
    void partialReturnAndQuantityValidationAndCancelledDraftLeavesStock() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Partial Return Shop");
        String token = owner.token();
        String product = createProduct(token, "Mouse", "MOUSE-M8", "0", "PCS", "2000.00");
        post(token, "/api/v1/inventory/" + product + "/opening-stock", Map.of("quantity", new BigDecimal("20.000")));
        String customerId = createCustomer(token, "Meena", "9888810002");
        JsonNode sale = completeSale(token, customerId, product, "10", "2000.00", "0", null, "0");
        String saleId = sale.path("id").asText();
        String saleItemId = sale.path("items").get(0).path("id").asText();
        assertThat(stock(token, product)).isEqualByComparingTo("10");

        JsonNode first = mapper.readTree(post(token, "/api/v1/returns", returnBody(saleId, saleItemId, "3", "NOT_REQUIRED"))
                        .getBody())
                .path("data");
        post(token, "/api/v1/returns/" + first.path("id").asText() + "/complete", Map.of());
        assertThat(stock(token, product)).isEqualByComparingTo("13");

        JsonNode saleAfter = mapper.readTree(get(token, "/api/v1/sales/" + saleId).getBody()).path("data");
        assertThat(saleAfter.path("items").get(0).path("quantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(saleAfter.path("items").get(0).path("returnedQuantity").decimalValue()).isEqualByComparingTo("3");
        assertThat(saleAfter.path("returnStatus").asText()).isEqualTo("PARTIALLY_RETURNED");
        assertThat(saleAfter.path("customerCreditAmount").decimalValue()).isEqualByComparingTo("6000.00");

        ResponseEntity<String> tooMany = post(token, "/api/v1/returns", returnBody(saleId, saleItemId, "8", "OTHER"));
        assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapper.readTree(tooMany.getBody()).path("error").path("code").asText())
                .isEqualTo("RETURN_QUANTITY_EXCEEDS_AVAILABLE");

        JsonNode cancelled = mapper.readTree(post(token, "/api/v1/returns", returnBody(saleId, saleItemId, "1", "OTHER"))
                        .getBody())
                .path("data");
        post(token, "/api/v1/returns/" + cancelled.path("id").asText() + "/cancel", Map.of());
        assertThat(stock(token, product)).isEqualByComparingTo("13");
        ResponseEntity<String> completeCancelled =
                post(token, "/api/v1/returns/" + cancelled.path("id").asText() + "/complete", Map.of());
        assertThat(completeCancelled.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stock(token, product)).isEqualByComparingTo("13");
    }

    @Test
    void partialPaymentThenReturnRecalculatesReceivableOrCredit() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Receivable Shop");
        String token = owner.token();
        String product = createProduct(token, "Filter", "FIL-M8", "0", "PCS", "10000.00");
        post(token, "/api/v1/inventory/" + product + "/opening-stock", Map.of("quantity", new BigDecimal("5.000")));
        String customerId = createCustomer(token, "Arun", "9888810003");

        JsonNode sale = completeSale(token, customerId, product, "1", "10000.00", "0", "4000.00", "0");
        assertThat(sale.path("outstandingAmount").decimalValue()).isEqualByComparingTo("6000.00");
        String saleItemId = sale.path("items").get(0).path("id").asText();

        JsonNode ret = mapper.readTree(post(
                        token, "/api/v1/returns", returnBody(sale.path("id").asText(), saleItemId, "1", "QUALITY_ISSUE"))
                .getBody())
                .path("data");
        post(token, "/api/v1/returns/" + ret.path("id").asText() + "/complete", Map.of());
        JsonNode after = mapper.readTree(get(token, "/api/v1/sales/" + sale.path("id").asText()).getBody()).path("data");
        assertThat(after.path("netSaleAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(after.path("outstandingAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(after.path("customerCreditAmount").decimalValue()).isEqualByComparingTo("4000.00");
        assertThat(after.path("paymentStatus").asText()).isEqualTo("REFUND_DUE");
    }

    @Test
    void refundExceedsCreditAndCashierCannotCompleteRefund() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Refund Guard Shop");
        String token = owner.token();
        String cashier = cashierToken(owner);
        String product = createProduct(token, "Jug", "JUG-M8", "0", "PCS", "5000.00");
        post(token, "/api/v1/inventory/" + product + "/opening-stock", Map.of("quantity", new BigDecimal("5.000")));
        String customerId = createCustomer(token, "Kiran", "9888810004");
        JsonNode sale = completeSale(token, customerId, product, "1", "5000.00", "0", null, "0");
        String saleItemId = sale.path("items").get(0).path("id").asText();
        JsonNode ret = mapper.readTree(post(token, "/api/v1/returns", returnBody(sale.path("id").asText(), saleItemId, "1", "OTHER"))
                        .getBody())
                .path("data");
        post(cashier, "/api/v1/returns/" + ret.path("id").asText() + "/complete", Map.of());

        ResponseEntity<String> over = post(
                token,
                "/api/v1/refunds",
                Map.of(
                        "saleId",
                        sale.path("id").asText(),
                        "amount",
                        new BigDecimal("6000.00"),
                        "paymentMethod",
                        "CASH"));
        assertThat(over.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.readTree(over.getBody()).path("error").path("code").asText())
                .isEqualTo("REFUND_EXCEEDS_AVAILABLE_CREDIT");

        JsonNode pending = mapper.readTree(post(
                        cashier,
                        "/api/v1/refunds",
                        Map.of(
                                "saleId",
                                sale.path("id").asText(),
                                "amount",
                                new BigDecimal("1000.00"),
                                "paymentMethod",
                                "CASH"))
                .getBody())
                .path("data");
        ResponseEntity<String> forbidden =
                post(cashier, "/api/v1/refunds/" + pending.path("id").asText() + "/complete", Map.of());
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        post(token, "/api/v1/refunds/" + pending.path("id").asText() + "/complete", Map.of());
        JsonNode after = mapper.readTree(get(token, "/api/v1/sales/" + sale.path("id").asText()).getBody()).path("data");
        assertThat(after.path("customerCreditAmount").decimalValue()).isEqualByComparingTo("4000.00");
    }

    @Test
    void tenantBCannotReadTenantAReturn() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Tenant A Returns");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Tenant B Returns");
        String product = createProduct(a.token(), "Bottle", "BOT-M8", "0", "PCS", "100.00");
        post(a.token(), "/api/v1/inventory/" + product + "/opening-stock", Map.of("quantity", new BigDecimal("5.000")));
        String customerId = createCustomer(a.token(), "Asha", "9888810005");
        JsonNode sale = completeSale(a.token(), customerId, product, "1", "100.00", "0", null, "0");
        JsonNode ret = mapper.readTree(post(
                        a.token(),
                        "/api/v1/returns",
                        returnBody(sale.path("id").asText(), sale.path("items").get(0).path("id").asText(), "1", "OTHER"))
                .getBody())
                .path("data");
        ResponseEntity<String> hidden = get(b.token(), "/api/v1/returns/" + ret.path("id").asText());
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void concurrentCompletesCannotReturnTheSameQuantityTwice() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Lock Return Shop");
        String token = owner.token();
        String product = createProduct(token, "Cable", "CAB-M8", "0", "PCS", "100.00");
        post(token, "/api/v1/inventory/" + product + "/opening-stock", Map.of("quantity", new BigDecimal("10.000")));
        String customerId = createCustomer(token, "Dev", "9888810006");
        JsonNode sale = completeSale(token, customerId, product, "5", "100.00", "0", null, "0");
        String saleItemId = sale.path("items").get(0).path("id").asText();
        String saleId = sale.path("id").asText();
        JsonNode draftA = mapper.readTree(post(token, "/api/v1/returns", returnBody(saleId, saleItemId, "5", "OTHER"))
                        .getBody())
                .path("data");
        JsonNode draftB = mapper.readTree(post(token, "/api/v1/returns", returnBody(saleId, saleItemId, "5", "OTHER"))
                        .getBody())
                .path("data");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger ok = new AtomicInteger();
        try {
            Future<Integer> a = pool.submit(() -> completeWhenReady(token, draftA.path("id").asText(), start));
            Future<Integer> b = pool.submit(() -> completeWhenReady(token, draftB.path("id").asText(), start));
            start.countDown();
            int first = a.get(30, TimeUnit.SECONDS);
            int second = b.get(30, TimeUnit.SECONDS);
            if (first == 200) {
                ok.incrementAndGet();
            }
            if (second == 200) {
                ok.incrementAndGet();
            }
            assertThat(ok.get()).isEqualTo(1);
            assertThat(stock(token, product)).isEqualByComparingTo("10");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void pipelineAdvanceIsNotDuplicatedAfterReturn() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Pipeline Return Co", "APPLIANCES_WATER_PURIFIER", "PIPELINE");
        String token = owner.token();
        String product = createProduct(token, "Kent Mini", "KENT-MINI", "0", "PCS", "50000.00");
        post(token, "/api/v1/inventory/" + product + "/opening-stock", Map.of("quantity", new BigDecimal("10.000")));
        Map<String, Object> leadBody = new HashMap<>();
        leadBody.put("name", "Ravi Kumar");
        leadBody.put("phone", "9876599001");
        leadBody.put("source", "WALK_IN");
        leadBody.put("priority", "HIGH");
        String leadId = mapper.readTree(post(token, "/api/v1/leads", leadBody).getBody()).path("data").path("id").asText();
        String customerId = mapper.readTree(post(token, "/api/v1/leads/" + leadId + "/convert-customer", Map.of()).getBody())
                .path("data")
                .path("id")
                .asText();
        Map<String, Object> quotation = new HashMap<>();
        quotation.put("leadId", leadId);
        quotation.put("customerId", customerId);
        quotation.put("quotationDate", LocalDate.now().toString());
        quotation.put("validUntil", LocalDate.now().plusDays(10).toString());
        quotation.put("items", List.of(Map.of(
                "productId", product, "quantity", new BigDecimal("1"), "unitPrice", new BigDecimal("50000.00"), "gstRate", new BigDecimal("0"))));
        String quotationId = mapper.readTree(post(token, "/api/v1/quotations", quotation).getBody()).path("data").path("id").asText();
        post(token, "/api/v1/quotations/" + quotationId + "/send", Map.of());
        post(token, "/api/v1/quotations/" + quotationId + "/accept", Map.of());
        String orderId = mapper.readTree(post(token, "/api/v1/sales-orders/from-quotation/" + quotationId, Map.of()).getBody())
                .path("data")
                .path("id")
                .asText();
        post(token, "/api/v1/sales-orders/" + orderId + "/confirm", Map.of());
        Map<String, Object> advance = new HashMap<>();
        advance.put("salesOrderId", orderId);
        advance.put("amount", new BigDecimal("10000.00"));
        advance.put("paymentMethod", "UPI");
        advance.put("paymentDate", LocalDate.now().toString());
        post(token, "/api/v1/payments", advance);
        JsonNode sale = mapper.readTree(post(token, "/api/v1/sales-orders/" + orderId + "/convert-sale", Map.of()).getBody())
                .path("data");
        assertThat(sale.path("actualPaidAmount").decimalValue()).isEqualByComparingTo("10000.00");
        assertThat(sale.path("outstandingAmount").decimalValue()).isEqualByComparingTo("40000.00");
        String saleItemId = mapper.readTree(get(token, "/api/v1/sales/" + sale.path("id").asText()).getBody())
                .path("data")
                .path("items")
                .get(0)
                .path("id")
                .asText();
        JsonNode ret = mapper.readTree(post(
                        token, "/api/v1/returns", returnBody(sale.path("id").asText(), saleItemId, "1", "CUSTOMER_CHANGED_MIND"))
                .getBody())
                .path("data");
        post(token, "/api/v1/returns/" + ret.path("id").asText() + "/complete", Map.of());
        JsonNode after = mapper.readTree(get(token, "/api/v1/sales/" + sale.path("id").asText()).getBody()).path("data");
        assertThat(after.path("actualPaidAmount").decimalValue()).isEqualByComparingTo("10000.00");
        assertThat(after.path("netSaleAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(after.path("customerCreditAmount").decimalValue()).isEqualByComparingTo("10000.00");
        JsonNode payments = mapper.readTree(get(token, "/api/v1/payments?saleId=" + sale.path("id").asText()).getBody())
                .path("data");
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).path("amount").decimalValue()).isEqualByComparingTo("10000.00");
    }

    private int completeWhenReady(String token, String returnId, CountDownLatch start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        return post(token, "/api/v1/returns/" + returnId + "/complete", Map.of()).getStatusCode().value();
    }

    private JsonNode completeSale(
            String token,
            String customerId,
            String productId,
            String qty,
            String price,
            String credit,
            String payment,
            String gst)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("saleDate", LocalDate.now().toString());
        body.put("discount", new BigDecimal("0"));
        body.put("items", List.of(Map.of(
                "productId",
                productId,
                "quantity",
                new BigDecimal(qty),
                "unitPrice",
                new BigDecimal(price),
                "gstRate",
                new BigDecimal(gst))));
        String saleId = mapper.readTree(post(token, "/api/v1/sales", body).getBody()).path("data").path("id").asText();
        Map<String, Object> complete = new HashMap<>();
        complete.put("paymentMethod", "CASH");
        if (new BigDecimal(credit).compareTo(BigDecimal.ZERO) > 0) {
            complete.put("creditAmount", new BigDecimal(credit));
        }
        if (payment != null) {
            complete.put("paymentAmount", new BigDecimal(payment));
        }
        ResponseEntity<String> response = post(token, "/api/v1/sales/" + saleId + "/complete", complete);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody()).path("data");
    }

    private Map<String, Object> returnBody(String saleId, String saleItemId, String qty, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("saleId", saleId);
        body.put("reason", reason);
        body.put("items", List.of(Map.of("saleItemId", saleItemId, "quantity", new BigDecimal(qty), "reason", reason)));
        return body;
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

    private String createCustomer(String token, String name, String phone) throws Exception {
        return mapper.readTree(post(token, "/api/v1/customers", Map.of("name", name, "phone", phone)).getBody())
                .path("data")
                .path("id")
                .asText();
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

    private ResponseEntity<String> get(String token, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(AuthTestSupport.bearer(token)), String.class);
    }

    private ResponseEntity<String> post(String token, String path, Object body) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, AuthTestSupport.bearer(token)), String.class);
    }
}
