package in.retailflow.api.catalog.web;

import in.retailflow.api.catalog.dto.ProductRequest;
import in.retailflow.api.catalog.dto.ProductResponse;
import in.retailflow.api.catalog.dto.StatusUpdateRequest;
import in.retailflow.api.catalog.service.ProductService;
import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.common.api.PageResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PageResponse<ProductResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(productService.list(q, categoryId, active, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<ProductResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(productService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<ProductResponse> update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<ProductResponse> status(@PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.ok(productService.updateStatus(id, request.active()));
    }
}
