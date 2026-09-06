package in.retailflow.api.sales.web;

import in.retailflow.api.catalog.dto.StatusUpdateRequest;
import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.common.api.PageResponse;
import in.retailflow.api.sales.dto.CreditTransactionResponse;
import in.retailflow.api.sales.dto.CustomerCreditResponse;
import in.retailflow.api.sales.dto.CustomerFinancialResponse;
import in.retailflow.api.sales.dto.CustomerRequest;
import in.retailflow.api.sales.dto.CustomerResponse;
import in.retailflow.api.sales.dto.CustomerSummaryResponse;
import in.retailflow.api.sales.dto.RefundResponse;
import in.retailflow.api.sales.dto.SaleReturnResponse;
import in.retailflow.api.sales.service.CustomerCreditService;
import in.retailflow.api.sales.service.CustomerService;
import in.retailflow.api.sales.service.RefundService;
import in.retailflow.api.sales.service.SaleReturnService;
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
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerCreditService creditService;
    private final SaleReturnService saleReturnService;
    private final RefundService refundService;

    public CustomerController(
            CustomerService customerService,
            CustomerCreditService creditService,
            SaleReturnService saleReturnService,
            RefundService refundService) {
        this.customerService = customerService;
        this.creditService = creditService;
        this.saleReturnService = saleReturnService;
        this.refundService = refundService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PageResponse<CustomerResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(customerService.list(q, active, page, size));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<CustomerSummaryResponse> summary() {
        return ApiResponse.ok(customerService.summary());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<List<CustomerResponse>> active() {
        return ApiResponse.ok(customerService.listActive());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<CustomerResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(customerService.get(id));
    }

    @GetMapping("/{id}/credit")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<CustomerCreditResponse> credit(@PathVariable UUID id) {
        return ApiResponse.ok(creditService.forCustomer(id));
    }

    @GetMapping("/{id}/credit-transactions")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<List<CreditTransactionResponse>> creditTransactions(@PathVariable UUID id) {
        return ApiResponse.ok(creditService.transactions(id));
    }

    @GetMapping("/{id}/financial")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<CustomerFinancialResponse> financial(@PathVariable UUID id) {
        return ApiResponse.ok(creditService.financial(id));
    }

    @GetMapping("/{id}/returns")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PageResponse<SaleReturnResponse>> returns(@PathVariable UUID id) {
        return ApiResponse.ok(saleReturnService.list(null, id, null, null, null, null, 1, 100));
    }

    @GetMapping("/{id}/refunds")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<List<RefundResponse>> refunds(@PathVariable UUID id) {
        return ApiResponse.ok(refundService.list(null, null, null, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok(customerService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<CustomerResponse> update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok(customerService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ApiResponse<CustomerResponse> status(@PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request) {
        return ApiResponse.ok(customerService.updateStatus(id, request));
    }
}
