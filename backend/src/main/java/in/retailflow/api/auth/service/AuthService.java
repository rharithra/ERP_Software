package in.retailflow.api.auth.service;

import in.retailflow.api.auth.dto.AuthResponse;
import in.retailflow.api.auth.dto.AuthResponse.AuthenticatedUser;
import in.retailflow.api.auth.dto.AuthResponse.TenantSummary;
import in.retailflow.api.auth.dto.LoginRequest;
import in.retailflow.api.auth.dto.MeResponse;
import in.retailflow.api.auth.dto.SignupRequest;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.identity.domain.TenantRole;
import in.retailflow.api.identity.domain.UserAccount;
import in.retailflow.api.identity.repository.UserAccountRepository;
import in.retailflow.api.security.jwt.JwtService;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.security.tenant.TenantContext.TenantPrincipal;
import in.retailflow.api.security.tenant.TenantSessionBinder;
import in.retailflow.api.tenant.domain.Tenant;
import in.retailflow.api.tenant.domain.TenantMembership;
import in.retailflow.api.tenant.repository.TenantMembershipRepository;
import in.retailflow.api.tenant.repository.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository userAccountRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TenantSessionBinder tenantSessionBinder;

    public AuthService(
            UserAccountRepository userAccountRepository,
            TenantRepository tenantRepository,
            TenantMembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TenantSessionBinder tenantSessionBinder) {
        this.userAccountRepository = userAccountRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tenantSessionBinder = tenantSessionBinder;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        tenantSessionBinder.enableRlsBypass();
        String email = request.email().trim().toLowerCase();
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new RetailflowException(
                    ErrorCodes.EMAIL_ALREADY_REGISTERED,
                    "An account with this email already exists",
                    HttpStatus.CONFLICT.value());
        }

        Tenant tenant = new Tenant(UUID.randomUUID(), request.companyName().trim());
        tenantRepository.save(tenant);

        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim());
        userAccountRepository.save(user);

        TenantMembership membership =
                new TenantMembership(UUID.randomUUID(), tenant, user, TenantRole.OWNER);
        membershipRepository.save(membership);

        log.info("Created tenant {} with owner user {}", tenant.getId(), user.getId());
        return toAuthResponse(user, tenant, TenantRole.OWNER);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        tenantSessionBinder.enableRlsBypass();
        String email = request.email().trim().toLowerCase();
        UserAccount user = userAccountRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(AuthService::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        List<TenantMembership> memberships = membershipRepository.findByUserIdWithTenant(user.getId());
        if (memberships.isEmpty()) {
            throw invalidCredentials();
        }
        TenantMembership membership = memberships.getFirst();
        return toAuthResponse(user, membership.getTenant(), membership.getRole());
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        TenantPrincipal principal = TenantContext.require();
        tenantSessionBinder.bindCurrentTenant(principal.tenantId());
        Tenant tenant = tenantRepository
                .findById(principal.tenantId())
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.TENANT_NOT_FOUND, "Company not found", HttpStatus.NOT_FOUND.value()));
        return new MeResponse(
                principal.userId().toString(),
                principal.email(),
                principal.fullName(),
                principal.role(),
                new TenantSummary(tenant.getId().toString(), tenant.getName(), tenant.getCurrency(), tenant.getTimezone()));
    }

    private AuthResponse toAuthResponse(UserAccount user, Tenant tenant, TenantRole role) {
        String token = jwtService.issueToken(user.getId(), user.getEmail(), tenant.getId(), role);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                role.name(),
                new TenantSummary(
                        tenant.getId().toString(), tenant.getName(), tenant.getCurrency(), tenant.getTimezone()));
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs() / 1000, authenticatedUser);
    }

    private static RetailflowException invalidCredentials() {
        return new RetailflowException(
                ErrorCodes.INVALID_CREDENTIALS, "Invalid email or password", HttpStatus.UNAUTHORIZED.value());
    }
}
