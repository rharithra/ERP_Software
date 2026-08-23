package in.retailflow.api.security.jwt;

import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.security.tenant.TenantContext.TenantPrincipal;
import in.retailflow.api.tenant.domain.TenantMembership;
import in.retailflow.api.tenant.service.MembershipQueryService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final MembershipQueryService membershipQueryService;

    public JwtAuthenticationFilter(JwtService jwtService, MembershipQueryService membershipQueryService) {
        this.jwtService = jwtService;
        this.membershipQueryService = membershipQueryService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                authenticate(token);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwtService.parse(token);
            UUID userId = UUID.fromString(claims.getSubject());
            UUID tenantId = UUID.fromString(claims.get("tenantId", String.class));
            Optional<TenantMembership> membership = membershipQueryService.findActiveMembership(userId, tenantId);
            if (membership.isEmpty()) {
                log.warn("Rejected token for user without tenant membership");
                return;
            }
            TenantMembership m = membership.get();
            String role = m.getRole().name();
            TenantPrincipal principal = new TenantPrincipal(
                    userId, tenantId, m.getUser().getEmail(), role, m.getUser().getFullName());
            TenantContext.set(principal);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT rejected");
        }
    }
}
