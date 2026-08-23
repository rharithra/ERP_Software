package in.retailflow.api.tenant.service;

import in.retailflow.api.security.tenant.TenantBypass;
import in.retailflow.api.tenant.domain.TenantMembership;
import in.retailflow.api.tenant.repository.TenantMembershipRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipQueryService {

    private final TenantMembershipRepository membershipRepository;

    public MembershipQueryService(TenantMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @TenantBypass
    @Transactional(readOnly = true)
    public Optional<TenantMembership> findActiveMembership(UUID userId, UUID tenantId) {
        return membershipRepository.findByUserIdAndTenantId(userId, tenantId);
    }
}
