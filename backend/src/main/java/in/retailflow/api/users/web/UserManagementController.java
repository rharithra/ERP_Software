package in.retailflow.api.users.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.users.dto.CreateUserRequest;
import in.retailflow.api.users.dto.ResetPasswordRequest;
import in.retailflow.api.users.dto.TenantUserResponse;
import in.retailflow.api.users.dto.UpdateUserRoleRequest;
import in.retailflow.api.users.dto.UpdateUserStatusRequest;
import in.retailflow.api.users.service.UserManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('OWNER')")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public ApiResponse<List<TenantUserResponse>> list() {
        return ApiResponse.ok(userManagementService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<TenantUserResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(userManagementService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TenantUserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userManagementService.create(request));
    }

    @PatchMapping("/{id}/role")
    public ApiResponse<TenantUserResponse> changeRole(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserRoleRequest request) {
        return ApiResponse.ok(userManagementService.changeRole(id, request.role()));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<TenantUserResponse> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.ok(userManagementService.changeStatus(id, request.status()));
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        userManagementService.resetPassword(id, request.temporaryPassword());
        return ApiResponse.ok(null);
    }
}
