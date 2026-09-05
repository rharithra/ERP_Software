package in.retailflow.api.users.service;

import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.identity.domain.MembershipStatus;
import in.retailflow.api.identity.domain.TenantRole;
import in.retailflow.api.identity.domain.UserAccount;
import in.retailflow.api.identity.domain.UserManagementEvent;
import in.retailflow.api.identity.domain.UserManagementEventType;
import in.retailflow.api.identity.repository.UserAccountRepository;
import in.retailflow.api.identity.repository.UserManagementEventRepository;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import in.retailflow.api.tenant.domain.TenantMembership;
import in.retailflow.api.tenant.repository.TenantMembershipRepository;
import in.retailflow.api.tenant.repository.TenantRepository;
import in.retailflow.api.users.dto.CreateUserRequest;
import in.retailflow.api.users.dto.TenantUserResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {

    private final TenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserManagementEventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(
            TenantMembershipRepository membershipRepository,
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            UserManagementEventRepository eventRepository,
            PasswordEncoder passwordEncoder) {
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.eventRepository = eventRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<TenantUserResponse> list() {
        return membershipRepository.findByTenantIdWithUser(TenantContext.requireTenantId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantUserResponse get(UUID userId) {
        return toResponse(requireMembership(userId));
    }

    @Transactional
    public TenantUserResponse create(CreateUserRequest request) {
        TenantRole role = requireStaffRole(request.role());
        String email = request.email().trim().toLowerCase();
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new RetailflowException(
                    ErrorCodes.EMAIL_ALREADY_REGISTERED,
                    "An account with this email already exists",
                    HttpStatus.CONFLICT.value());
        }
        Tenant tenant = currentTenant();
        UserAccount user = new UserAccount(
                UUID.randomUUID(), email, passwordEncoder.encode(request.temporaryPassword()), request.fullName().trim());
        userAccountRepository.save(user);
        TenantMembership membership = new TenantMembership(UUID.randomUUID(), tenant, user, role);
        membershipRepository.save(membership);
        record(
                tenant,
                user.getId(),
                UserManagementEventType.USER_CREATED,
                "Created " + role.name() + " " + user.getFullName());
        return toResponse(membership);
    }

    @Transactional
    public TenantUserResponse changeRole(UUID userId, TenantRole nextRole) {
        TenantRole role = requireStaffRole(nextRole);
        TenantMembership membership = requireStaffTarget(userId);
        TenantRole previous = membership.getRole();
        if (previous == role) {
            return toResponse(membership);
        }
        membership.setRole(role);
        record(
                membership.getTenant(),
                userId,
                UserManagementEventType.USER_ROLE_CHANGED,
                "Changed role from " + previous.name() + " to " + role.name());
        return toResponse(membership);
    }

    @Transactional
    public TenantUserResponse changeStatus(UUID userId, MembershipStatus status) {
        TenantMembership membership = requireStaffTarget(userId);
        if (status == membership.getStatus()) {
            return toResponse(membership);
        }
        membership.setStatus(status);
        UserManagementEventType type =
                status == MembershipStatus.ACTIVE
                        ? UserManagementEventType.USER_REACTIVATED
                        : UserManagementEventType.USER_DEACTIVATED;
        String message =
                status == MembershipStatus.ACTIVE
                        ? "Reactivated " + membership.getUser().getFullName()
                        : "Deactivated " + membership.getUser().getFullName();
        record(membership.getTenant(), userId, type, message);
        return toResponse(membership);
    }

    @Transactional
    public void resetPassword(UUID userId, String temporaryPassword) {
        TenantMembership membership = requireStaffTarget(userId);
        membership.getUser().setPasswordHash(passwordEncoder.encode(temporaryPassword));
        record(
                membership.getTenant(),
                userId,
                UserManagementEventType.USER_PASSWORD_RESET,
                "Reset password for " + membership.getUser().getFullName());
    }

    private TenantMembership requireStaffTarget(UUID userId) {
        if (userId.equals(TenantContext.require().userId())) {
            throw new RetailflowException(
                    ErrorCodes.CANNOT_MODIFY_SELF,
                    "You cannot change your own role, status, or password from user management",
                    HttpStatus.CONFLICT.value());
        }
        TenantMembership membership = requireMembership(userId);
        if (membership.getRole() == TenantRole.OWNER) {
            throw new RetailflowException(
                    ErrorCodes.CANNOT_MODIFY_OWNER,
                    "The owner account cannot be modified here",
                    HttpStatus.CONFLICT.value());
        }
        return membership;
    }

    private TenantMembership requireMembership(UUID userId) {
        return membershipRepository
                .findByUserIdAndTenantId(userId, TenantContext.requireTenantId())
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.MEMBERSHIP_NOT_FOUND, "User not found in this business", HttpStatus.NOT_FOUND.value()));
    }

    private static TenantRole requireStaffRole(TenantRole role) {
        if (role == null || role == TenantRole.OWNER) {
            throw new RetailflowException(
                    ErrorCodes.INVALID_ROLE, "New users must be MANAGER or CASHIER", HttpStatus.BAD_REQUEST.value());
        }
        return role;
    }

    private Tenant currentTenant() {
        return tenantRepository
                .findById(TenantContext.requireTenantId())
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.TENANT_NOT_FOUND, "Company not found", HttpStatus.NOT_FOUND.value()));
    }

    private void record(Tenant tenant, UUID targetUserId, UserManagementEventType type, String message) {
        eventRepository.save(new UserManagementEvent(
                UUID.randomUUID(), tenant, TenantContext.require().userId(), targetUserId, type, message));
    }

    private TenantUserResponse toResponse(TenantMembership membership) {
        UserAccount user = membership.getUser();
        return new TenantUserResponse(
                user.getId().toString(),
                user.getFullName(),
                user.getEmail(),
                membership.getRole(),
                membership.getStatus(),
                membership.getCreatedAt());
    }
}
