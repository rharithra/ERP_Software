package in.retailflow.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class AuthTestSupport {

    private AuthTestSupport() {}

    public static SignupResult signup(TestRestTemplate rest, ObjectMapper mapper, String companyName)
            throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@retailflow.test";
        Map<String, String> body = Map.of(
                "fullName", "Ananya Sharma",
                "email", email,
                "password", "SecurePass9",
                "companyName", companyName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response =
                rest.postForEntity("/api/v1/auth/signup", new HttpEntity<>(body, headers), String.class);
        JsonNode root = mapper.readTree(response.getBody());
        return new SignupResult(response.getStatusCode().value(), root, email);
    }

    public static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    public record SignupResult(int status, JsonNode body, String email) {
        public String token() {
            return body.path("data").path("accessToken").asText();
        }

        public String tenantId() {
            return body.path("data").path("user").path("tenant").path("id").asText();
        }

        public String userId() {
            return body.path("data").path("user").path("id").asText();
        }

        public String role() {
            return body.path("data").path("user").path("role").asText();
        }
    }
}
