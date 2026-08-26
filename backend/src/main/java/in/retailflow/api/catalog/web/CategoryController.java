package in.retailflow.api.catalog.web;

import in.retailflow.api.catalog.dto.CategoryRequest;
import in.retailflow.api.catalog.dto.CategoryResponse;
import in.retailflow.api.catalog.dto.StatusUpdateRequest;
import in.retailflow.api.catalog.service.CategoryService;
import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PageResponse<CategoryResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(categoryService.list(q, active, page, size));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<List<CategoryResponse>> active() {
        return ApiResponse.ok(categoryService.listActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<CategoryResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(categoryService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<CategoryResponse> update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<CategoryResponse> status(
            @PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.ok(categoryService.updateStatus(id, request.active()));
    }
}
