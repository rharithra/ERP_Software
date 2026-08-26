package in.retailflow.api.catalog.service;

import in.retailflow.api.catalog.domain.Category;
import in.retailflow.api.catalog.domain.GstRate;
import in.retailflow.api.catalog.domain.Product;
import in.retailflow.api.catalog.domain.ProductUnit;
import in.retailflow.api.catalog.dto.ProductRequest;
import in.retailflow.api.catalog.dto.ProductResponse;
import in.retailflow.api.catalog.repository.CategoryRepository;
import in.retailflow.api.catalog.repository.ProductRepository;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import in.retailflow.api.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TenantRepository tenantRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            TenantRepository tenantRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String query, UUID categoryId, Boolean active, int page, int size) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), clampSize(size), Sort.by("name").ascending());
        return PageResponse.from(
                productRepository.search(blankToNull(query), categoryId, active, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(requireProduct(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Tenant tenant = currentTenant();
        Category category = requireCategory(parseUuid(request.categoryId(), "categoryId"));
        String sku = request.sku().trim();
        String barcode = blankToNull(request.barcode());
        ensureSkuAvailable(sku, null);
        ensureBarcodeAvailable(barcode, null);
        GstRate gstRate = parseGst(request.gstRate());
        ProductUnit unit = parseUnit(request.unit());
        Product product = new Product(
                UUID.randomUUID(),
                tenant,
                category,
                request.name().trim(),
                sku,
                barcode,
                blankToNull(request.description()),
                request.costPrice(),
                request.sellingPrice(),
                gstRate.percent(),
                unit);
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = requireProduct(id);
        Category category = requireCategory(parseUuid(request.categoryId(), "categoryId"));
        String sku = request.sku().trim();
        String barcode = blankToNull(request.barcode());
        ensureSkuAvailable(sku, id);
        ensureBarcodeAvailable(barcode, id);
        product.setName(request.name().trim());
        product.setCategory(category);
        product.setDescription(blankToNull(request.description()));
        product.setSku(sku);
        product.setBarcode(barcode);
        product.setCostPrice(request.costPrice());
        product.setSellingPrice(request.sellingPrice());
        product.setGstRate(parseGst(request.gstRate()).percent());
        product.setUnit(parseUnit(request.unit()));
        return toResponse(product);
    }

    @Transactional
    public ProductResponse updateStatus(UUID id, boolean active) {
        Product product = requireProduct(id);
        product.setActive(active);
        return toResponse(product);
    }

    private void ensureSkuAvailable(String sku, UUID excludeId) {
        if (productRepository.existsSku(sku, excludeId)) {
            throw new RetailflowException(
                    ErrorCodes.SKU_ALREADY_EXISTS,
                    "This SKU is already used by another product in your store",
                    HttpStatus.CONFLICT.value());
        }
    }

    private void ensureBarcodeAvailable(String barcode, UUID excludeId) {
        if (barcode == null) {
            return;
        }
        if (productRepository.existsBarcode(barcode, excludeId)) {
            throw new RetailflowException(
                    ErrorCodes.BARCODE_ALREADY_EXISTS,
                    "This barcode is already used by another product in your store",
                    HttpStatus.CONFLICT.value());
        }
    }

    private Product requireProduct(UUID id) {
        return productRepository
                .findById(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.PRODUCT_NOT_FOUND, "Product not found", HttpStatus.NOT_FOUND.value()));
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

    private GstRate parseGst(BigDecimal gstRate) {
        try {
            return GstRate.fromPercent(gstRate);
        } catch (IllegalArgumentException ex) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR,
                    "GST rate must be 0, 5, 12, 18, or 28 percent",
                    HttpStatus.BAD_REQUEST.value());
        }
    }

    private ProductUnit parseUnit(String unit) {
        try {
            return ProductUnit.valueOf(unit.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR,
                    "Unit must be one of PCS, KG, G, L, ML, BOX, PACK, BOTTLE",
                    HttpStatus.BAD_REQUEST.value());
        }
    }

    private UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR, "Invalid " + field, HttpStatus.BAD_REQUEST.value());
        }
    }

    private ProductResponse toResponse(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId().toString(),
                product.getName(),
                product.getSku(),
                product.getBarcode(),
                product.getDescription(),
                category.getId().toString(),
                category.getName(),
                product.getCostPrice(),
                product.getSellingPrice(),
                product.getGstRate(),
                product.getUnit().name(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
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
