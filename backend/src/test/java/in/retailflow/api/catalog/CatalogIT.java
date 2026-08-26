package in.retailflow.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.retailflow.api.support.AuthTestSupport;
import in.retailflow.api.support.AuthTestSupport.SignupResult;
import java.math.BigDecimal;
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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CatalogIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void categoryCrudSearchAndStatus() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Joshi Kirana");

        ResponseEntity<String> created = rest.exchange(
                "/api/v1/categories",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Dairy", "description", "Milk and curd"), AuthTestSupport.bearer(owner.token())),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode dairy = mapper.readTree(created.getBody()).path("data");
        String dairyId = dairy.path("id").asText();
        assertThat(dairy.path("name").asText()).isEqualTo("Dairy");
        assertThat(dairy.path("active").asBoolean()).isTrue();

        ResponseEntity<String> duplicate = rest.exchange(
                "/api/v1/categories",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "dairy"), AuthTestSupport.bearer(owner.token())),
                String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> invalid = rest.exchange(
                "/api/v1/categories",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "A"), AuthTestSupport.bearer(owner.token())),
                String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        rest.exchange(
                "/api/v1/categories",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Snacks"), AuthTestSupport.bearer(owner.token())),
                String.class);

        JsonNode list = mapper.readTree(rest.exchange(
                        "/api/v1/categories?q=dai",
                        HttpMethod.GET,
                        new HttpEntity<>(AuthTestSupport.bearer(owner.token())),
                        String.class)
                .getBody());
        assertThat(list.path("data").path("items")).hasSize(1);
        assertThat(list.path("data").path("items").get(0).path("name").asText()).isEqualTo("Dairy");

        ResponseEntity<String> updated = rest.exchange(
                "/api/v1/categories/" + dairyId,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Dairy Fresh", "description", "Chilled"), AuthTestSupport.bearer(owner.token())),
                String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(updated.getBody()).path("data").path("name").asText()).isEqualTo("Dairy Fresh");

        ResponseEntity<String> deactivated = rest.exchange(
                "/api/v1/categories/" + dairyId + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("active", false), AuthTestSupport.bearer(owner.token())),
                String.class);
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(deactivated.getBody()).path("data").path("active").asBoolean()).isFalse();
    }

    @Test
    void productCrudFiltersValidationAndUniqueness() throws Exception {
        SignupResult owner = AuthTestSupport.signup(rest, mapper, "Mehta Mart");
        String dairyId = createCategory(owner.token(), "Dairy");
        String snacksId = createCategory(owner.token(), "Snacks");

        Map<String, Object> milk = productBody("Amul Taaza 500ml", dairyId, "AMUL-MILK-500", "8901234567890", "24.00", "28.00", "5", "ML");
        ResponseEntity<String> created = rest.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(milk, AuthTestSupport.bearer(owner.token())),
                String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode product = mapper.readTree(created.getBody()).path("data");
        String productId = product.path("id").asText();
        assertThat(product.path("sku").asText()).isEqualTo("AMUL-MILK-500");
        assertThat(product.path("gstRate").decimalValue()).isEqualByComparingTo("5");

        ResponseEntity<String> dupSku = rest.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(
                        productBody("Other milk", dairyId, "amul-milk-500", null, "10.00", "12.00", "5", "ML"),
                        AuthTestSupport.bearer(owner.token())),
                String.class);
        assertThat(dupSku.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> dupBarcode = rest.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(
                        productBody("Other milk 2", dairyId, "AMUL-MILK-1L", "8901234567890", "10.00", "12.00", "5", "L"),
                        AuthTestSupport.bearer(owner.token())),
                String.class);
        assertThat(dupBarcode.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map<String, Object> negative = productBody("Bad", dairyId, "BAD-1", null, "-1.00", "10.00", "5", "PCS");
        ResponseEntity<String> negativePrice = rest.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(negative, AuthTestSupport.bearer(owner.token())),
                String.class);
        assertThat(negativePrice.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> badGst = productBody("Bad GST", dairyId, "BAD-GST", null, "10.00", "12.00", "7", "PCS");
        ResponseEntity<String> gstRejected = rest.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(badGst, AuthTestSupport.bearer(owner.token())),
                String.class);
        assertThat(gstRejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        rest.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(
                        productBody("Lays Classic", snacksId, "LAYS-CLASSIC", null, "8.00", "10.00", "18", "PACK"),
                        AuthTestSupport.bearer(owner.token())),
                String.class);

        JsonNode search = mapper.readTree(rest.exchange(
                        "/api/v1/products?q=amul",
                        HttpMethod.GET,
                        new HttpEntity<>(AuthTestSupport.bearer(owner.token())),
                        String.class)
                .getBody());
        assertThat(search.path("data").path("items")).hasSize(1);

        JsonNode byCategory = mapper.readTree(rest.exchange(
                        "/api/v1/products?categoryId=" + snacksId,
                        HttpMethod.GET,
                        new HttpEntity<>(AuthTestSupport.bearer(owner.token())),
                        String.class)
                .getBody());
        assertThat(byCategory.path("data").path("items")).hasSize(1);
        assertThat(byCategory.path("data").path("items").get(0).path("name").asText()).isEqualTo("Lays Classic");

        rest.exchange(
                "/api/v1/products/" + productId + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("active", false), AuthTestSupport.bearer(owner.token())),
                String.class);
        JsonNode inactive = mapper.readTree(rest.exchange(
                        "/api/v1/products?active=false",
                        HttpMethod.GET,
                        new HttpEntity<>(AuthTestSupport.bearer(owner.token())),
                        String.class)
                .getBody());
        assertThat(inactive.path("data").path("items")).hasSize(1);

        Map<String, Object> update = productBody("Amul Taaza 1L", dairyId, "AMUL-MILK-1L", "8909999999999", "40.00", "48.00", "5", "L");
        ResponseEntity<String> updated = rest.exchange(
                "/api/v1/products/" + productId,
                HttpMethod.PUT,
                new HttpEntity<>(update, AuthTestSupport.bearer(owner.token())),
                String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(updated.getBody()).path("data").path("sku").asText()).isEqualTo("AMUL-MILK-1L");
    }

    @Test
    void tenantACannotAccessTenantBCatalog() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Gupta Store");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Iyer Store");
        String categoryB = createCategory(b.token(), "Hidden Dairy");
        String productB = createProduct(b.token(), categoryB, "Secret Milk", "SEC-MILK");

        assertThat(listNames(a.token(), "/api/v1/categories")).doesNotContain("Hidden Dairy");
        assertThat(get(a.token(), "/api/v1/categories/" + categoryB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put(a.token(), "/api/v1/categories/" + categoryB, Map.of("name", "Hijack")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(patch(a.token(), "/api/v1/categories/" + categoryB + "/status", Map.of("active", false))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(listNames(a.token(), "/api/v1/products")).doesNotContain("Secret Milk");
        assertThat(get(a.token(), "/api/v1/products/" + productB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put(
                        a.token(),
                        "/api/v1/products/" + productB,
                        productBody("Hijack", categoryB, "HIJACK", null, "1.00", "2.00", "5", "PCS"))
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(patch(a.token(), "/api/v1/products/" + productB + "/status", Map.of("active", false)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        String categoryA = createCategory(a.token(), "Own Dairy");
        Map<String, Object> cross = productBody("Stolen", categoryB, "STOLEN-1", null, "1.00", "2.00", "5", "PCS");
        assertThat(post(a.token(), "/api/v1/products", cross).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> bodyWithTenant = new HashMap<>();
        bodyWithTenant.put("name", "Still Own");
        bodyWithTenant.put("tenantId", b.tenantId());
        bodyWithTenant.put("description", "ignored tenant");
        ResponseEntity<String> created = post(a.token(), "/api/v1/categories", bodyWithTenant);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode query = mapper.readTree(get(a.token(), "/api/v1/categories?tenantId=" + b.tenantId()).getBody());
        List<String> names = query.path("data").path("items").findValuesAsText("name");
        assertThat(names).contains("Still Own").doesNotContain("Hidden Dairy");

        assertThat(get(a.token(), "/api/v1/categories/" + b.tenantId()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        List<String> visibleB = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT set_config('app.bypass_rls', 'off', true)");
                statement.execute("SELECT set_config('app.current_tenant_id', '" + a.tenantId() + "', true)");
                var rs = statement.executeQuery("SELECT name FROM categories");
                java.util.ArrayList<String> found = new java.util.ArrayList<>();
                while (rs.next()) {
                    found.add(rs.getString(1));
                }
                connection.rollback();
                return found;
            }
        });
        assertThat(visibleB).doesNotContain("Hidden Dairy");
        assertThat(categoryA).isNotBlank();

        String sharedSku = createProduct(a.token(), categoryA, "Own Milk", "SEC-MILK");
        assertThat(sharedSku).isNotBlank();
    }

    private String createCategory(String token, String name) throws Exception {
        ResponseEntity<String> response = post(token, "/api/v1/categories", Map.of("name", name));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(response.getBody()).path("data").path("id").asText();
    }

    private String createProduct(String token, String categoryId, String name, String sku) throws Exception {
        ResponseEntity<String> response =
                post(token, "/api/v1/products", productBody(name, categoryId, sku, null, "10.00", "12.00", "5", "PCS"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return mapper.readTree(response.getBody()).path("data").path("id").asText();
    }

    private Map<String, Object> productBody(
            String name,
            String categoryId,
            String sku,
            String barcode,
            String cost,
            String selling,
            String gst,
            String unit) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryId", categoryId);
        body.put("sku", sku);
        body.put("barcode", barcode);
        body.put("costPrice", new BigDecimal(cost));
        body.put("sellingPrice", new BigDecimal(selling));
        body.put("gstRate", new BigDecimal(gst));
        body.put("unit", unit);
        return body;
    }

    private List<String> listNames(String token, String path) throws Exception {
        JsonNode body = mapper.readTree(get(token, path).getBody());
        return body.path("data").path("items").findValuesAsText("name");
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
