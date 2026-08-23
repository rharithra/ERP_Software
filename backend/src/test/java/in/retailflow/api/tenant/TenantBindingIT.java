package in.retailflow.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.retailflow.api.security.tenant.TenantBypass;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.security.tenant.TenantContext.TenantPrincipal;
import in.retailflow.api.support.AuthTestSupport;
import in.retailflow.api.support.AuthTestSupport.SignupResult;
import in.retailflow.api.tenant.domain.Tenant;
import in.retailflow.api.tenant.repository.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TenantBindingIT.ProbeConfig.class)
class TenantBindingIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TenantReadProbe tenantReadProbe;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void hibernateFilterAppliesWithoutServiceCallingBind() throws Exception {
        SignupResult a = AuthTestSupport.signup(rest, mapper, "Automatic Bind A");
        SignupResult b = AuthTestSupport.signup(rest, mapper, "Automatic Bind B");

        TenantContext.set(new TenantPrincipal(
                UUID.fromString(a.userId()),
                UUID.fromString(a.tenantId()),
                a.email(),
                "OWNER",
                "Ananya Sharma"));

        List<String> names = tenantReadProbe.listNames();
        assertThat(names).containsExactly("Automatic Bind A");
        assertThat(names).doesNotContain("Automatic Bind B");
        assertThat(b.tenantId()).isNotEqualTo(a.tenantId());
    }

    @Test
    void unboundTransactionFailsClosedAtDatabase() throws Exception {
        AuthTestSupport.signup(rest, mapper, "Unbound Store");

        List<Tenant> visible = new TransactionTemplate(transactionManager).execute(status -> tenantRepository.findAll());
        assertThat(visible).isEmpty();
    }

    @Test
    void rlsBypassDoesNotLeakIntoTheNextTransaction() throws Exception {
        SignupResult created = AuthTestSupport.signup(rest, mapper, "Bypass Leak Check");

        List<String> duringBypass = tenantReadProbe.listNamesWithBypass();
        assertThat(duringBypass).contains("Bypass Leak Check");

        List<Tenant> after = new TransactionTemplate(transactionManager).execute(status -> tenantRepository.findAll());
        assertThat(after).isEmpty();

        TenantContext.set(new TenantPrincipal(
                UUID.fromString(created.userId()),
                UUID.fromString(created.tenantId()),
                created.email(),
                "OWNER",
                "Ananya Sharma"));
        assertThat(tenantReadProbe.listNames()).containsExactly("Bypass Leak Check");
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        TenantReadProbe tenantReadProbe(TenantRepository tenantRepository) {
            return new TenantReadProbe(tenantRepository);
        }
    }

    static class TenantReadProbe {
        private final TenantRepository tenantRepository;

        TenantReadProbe(TenantRepository tenantRepository) {
            this.tenantRepository = tenantRepository;
        }

        @Transactional(readOnly = true)
        public List<String> listNames() {
            return tenantRepository.findAll().stream().map(Tenant::getName).toList();
        }

        @TenantBypass
        @Transactional(readOnly = true)
        public List<String> listNamesWithBypass() {
            return tenantRepository.findAll().stream().map(Tenant::getName).toList();
        }
    }
}
