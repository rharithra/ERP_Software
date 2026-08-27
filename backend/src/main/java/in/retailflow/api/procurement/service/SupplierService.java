package in.retailflow.api.procurement.service;

import in.retailflow.api.catalog.dto.StatusUpdateRequest;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.procurement.domain.Supplier;
import in.retailflow.api.procurement.dto.SupplierRequest;
import in.retailflow.api.procurement.dto.SupplierResponse;
import in.retailflow.api.procurement.dto.SupplierSummaryResponse;
import in.retailflow.api.procurement.repository.SupplierRepository;
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
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final TenantRepository tenantRepository;

    public SupplierService(SupplierRepository supplierRepository, TenantRepository tenantRepository) {
        this.supplierRepository = supplierRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(String query, Boolean active, int page, int size) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), clampSize(size), Sort.by("name").ascending());
        return PageResponse.from(supplierRepository.search(blankToNull(query), active, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> listActive() {
        return supplierRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierSummaryResponse summary() {
        Object[] row = supplierRepository.summarize();
        Object[] values = row.length == 3 && !(row[0] instanceof Object[]) ? row : (Object[]) row[0];
        return new SupplierSummaryResponse(asLong(values[0]), asLong(values[1]), asLong(values[2]));
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        Tenant tenant = currentTenant();
        String name = request.name().trim();
        ensureNameAvailable(name, null);
        Supplier supplier = new Supplier(UUID.randomUUID(), tenant, name);
        apply(supplier, request);
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = require(id);
        String name = request.name().trim();
        ensureNameAvailable(name, id);
        supplier.setName(name);
        apply(supplier, request);
        return toResponse(supplier);
    }

    @Transactional
    public SupplierResponse updateStatus(UUID id, StatusUpdateRequest request) {
        Supplier supplier = require(id);
        supplier.setActive(request.active());
        return toResponse(supplier);
    }

    Supplier requireActive(UUID id) {
        Supplier supplier = require(id);
        if (!supplier.isActive()) {
            throw new RetailflowException(
                    ErrorCodes.SUPPLIER_INACTIVE,
                    "This supplier is inactive and cannot be used on a new purchase",
                    HttpStatus.CONFLICT.value());
        }
        return supplier;
    }

    Supplier require(UUID id) {
        return supplierRepository
                .findById(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.SUPPLIER_NOT_FOUND, "Supplier not found", HttpStatus.NOT_FOUND.value()));
    }

    private void apply(Supplier supplier, SupplierRequest request) {
        supplier.setContactPerson(blankToNull(request.contactPerson()));
        supplier.setPhone(blankToNull(request.phone()));
        supplier.setEmail(blankToNull(request.email()));
        supplier.setAddress(blankToNull(request.address()));
        supplier.setGstin(blankToNull(request.gstin()));
        supplier.setNotes(blankToNull(request.notes()));
    }

    private void ensureNameAvailable(String name, UUID excludeId) {
        if (supplierRepository.existsName(name, excludeId)) {
            throw new RetailflowException(
                    ErrorCodes.SUPPLIER_NAME_TAKEN,
                    "A supplier with this name already exists in your store",
                    HttpStatus.CONFLICT.value());
        }
    }

    private Tenant currentTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.TENANT_NOT_FOUND, "Company not found", HttpStatus.NOT_FOUND.value()));
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId().toString(),
                supplier.getName(),
                supplier.getContactPerson(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getGstin(),
                supplier.getNotes(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt());
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

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
