package in.retailflow.api.pipeline.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.pipeline.dto.NotificationResponse;
import in.retailflow.api.pipeline.dto.PipelineDashboardResponse;
import in.retailflow.api.pipeline.service.NotificationService;
import in.retailflow.api.pipeline.service.PipelineDashboardService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PipelineDashboardController {

    private final PipelineDashboardService dashboardService;
    private final NotificationService notificationService;

    public PipelineDashboardController(
            PipelineDashboardService dashboardService, NotificationService notificationService) {
        this.dashboardService = dashboardService;
        this.notificationService = notificationService;
    }

    @GetMapping("/pipeline/dashboard")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<PipelineDashboardResponse> dashboard() {
        return ApiResponse.ok(dashboardService.dashboard());
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<List<NotificationResponse>> notifications() {
        return ApiResponse.ok(notificationService.listAndRefresh());
    }

    @GetMapping("/notifications/unread-count")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<Map<String, Long>> unread() {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount()));
    }

    @PostMapping("/notifications/{id}/read")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<Void> read(@PathVariable UUID id) {
        notificationService.markRead(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/notifications/read-all")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<Void> readAll() {
        notificationService.markAllRead();
        return ApiResponse.ok(null);
    }
}
