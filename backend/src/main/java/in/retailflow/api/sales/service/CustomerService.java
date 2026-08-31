package in.retailflow.api.sales.service;

import in.retailflow.api.catalog.dto.StatusUpdateRequest;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import in.retailflow.api.sales.domain.Customer;
import in.retailflow.api.sales.dto.CustomerRequest;
import in.retailflow.api.sales.dto.CustomerResponse;
import in.retailflow.api.sales.dto.CustomerSummaryResponse;
import in.retailflow.api.sales.repository.CustomerRepository;
import in.retailflow.api.security.tenant.TenantContext;
import in.retailflow.api.tenant.domain.Tenant;
import in.retailflow.api.tenant.repository.TenantRepository;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CustomerRepository customerRepository;
    private final TenantRepository tenantRepository;

    public CustomerService(CustomerRepository customerRepository, TenantRepository tenantRepository) {
        this.customerRepository = customerRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String query, Boolean active, int page, int size) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), clampSize(size), Sort.by("name").ascending());
        return PageResponse.from(customerRepository.search(blankToNull(query), active, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> listActive() {
        return customerRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerSummaryResponse summary() {
        Object[] row = customerRepository.summarize();
        Object[] values = row.length == 3 && !(row[0] instanceof Object[]) ? row : (Object[]) row[0];
        return new CustomerSummaryResponse(asLong(values[0]), asLong(values[1]), asLong(values[2]));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Tenant tenant = currentTenant();
        validateEmail(request.email());
        ensurePhoneAvailable(blankToNull(request.phone()), null);
        Customer customer = new Customer(UUID.randomUUID(), tenant, request.name().trim());
        apply(customer, request);
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer customer = require(id);
        validateEmail(request.email());
        ensurePhoneAvailable(blankToNull(request.phone()), id);
        customer.setName(request.name().trim());
        apply(customer, request);
        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse updateStatus(UUID id, StatusUpdateRequest request) {
        Customer customer = require(id);
        customer.setActive(request.active());
        return toResponse(customer);
    }

    public Customer requireActive(UUID id) {
        Customer customer = require(id);
        if (!customer.isActive()) {
            throw new RetailflowException(
                    ErrorCodes.CUSTOMER_INACTIVE,
                    "This customer is inactive and cannot be used on a new sale",
                    HttpStatus.CONFLICT.value());
        }
        return customer;
    }

    public Customer require(UUID id) {
        return customerRepository
                .findById(id)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.CUSTOMER_NOT_FOUND, "Customer not found", HttpStatus.NOT_FOUND.value()));
    }

    private void apply(Customer customer, CustomerRequest request) {
        customer.setPhone(blankToNull(request.phone()));
        customer.setEmail(blankToNull(request.email()));
        customer.setAddress(blankToNull(request.address()));
        customer.setGstin(blankToNull(request.gstin()));
        customer.setNotes(blankToNull(request.notes()));
    }

    private void ensurePhoneAvailable(String phone, UUID excludeId) {
        if (phone == null) {
            return;
        }
        if (customerRepository.existsPhone(phone, excludeId)) {
            throw new RetailflowException(
                    ErrorCodes.CUSTOMER_PHONE_TAKEN,
                    "A customer with this phone number already exists in your store",
                    HttpStatus.CONFLICT.value());
        }
    }

    private void validateEmail(String email) {
        String value = blankToNull(email);
        if (value != null && !EMAIL.matcher(value).matches()) {
            throw new RetailflowException(
                    ErrorCodes.VALIDATION_ERROR, "Enter a valid email address", HttpStatus.BAD_REQUEST.value());
        }
    }

    private Tenant currentTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new RetailflowException(
                        ErrorCodes.TENANT_NOT_FOUND, "Company not found", HttpStatus.NOT_FOUND.value()));
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId().toString(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getAddress(),
                customer.getGstin(),
                customer.getNotes(),
                customer.isActive(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
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
