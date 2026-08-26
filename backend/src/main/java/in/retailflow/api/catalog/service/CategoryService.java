package in.retailflow.api.catalog.service;

import in.retailflow.api.catalog.domain.Category;
import in.retailflow.api.catalog.dto.CategoryRequest;
import in.retailflow.api.catalog.dto.CategoryResponse;
import in.retailflow.api.catalog.repository.CategoryRepository;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import in.retailflow.api.tenant.repository.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TenantRepository tenantRepository;

    public CategoryService(CategoryRepository categoryRepository, TenantRepository tenantRepository) {
        this.categoryRepository = categoryRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(String query, Boolean active, int page, int size) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), clampSize(size), Sort.by("name").ascending());
        String q = blankToNull(query);
        return PageResponse.from(categoryRepository.search(q, active, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActive() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return toResponse(requireCategory(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Tenant tenant = currentTenant();
        String name = request.name().trim();
        ensureNameAvailable(name, null);
        Category category = new Category(UUID.randomUUID(), tenant, name, blankToNull(request.description()));
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = requireCategory(id);
        String name = request.name().trim();
        ensureNameAvailable(name, id);
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        return toResponse(category);
    }

    @Transactional
    public CategoryResponse updateStatus(UUID id, boolean active) {
        Category category = requireCategory(id);
        category.setActive(active);
        return toResponse(category);
    }

    private void ensureNameAvailable(String name, UUID excludeId) {
        if (categoryRepository.existsName(name, excludeId)) {
            throw new RetailflowException(
                    ErrorCodes.CATEGORY_NAME_TAKEN,
                    "A category with this name already exists in your store",
                    HttpStatus.CONFLICT.value());
        }
    }

    private Category requireCategory(UUID id) {
        return categoryRepository
                .findById(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.CATEGORY_NOT_FOUND, "Category not found", HttpStatus.NOT_FOUND.value()));
    }

    private Tenant currentTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.TENANT_NOT_FOUND, "Company not found", HttpStatus.NOT_FOUND.value()));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId().toString(),
                category.getName(),
                category.getDescription(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
