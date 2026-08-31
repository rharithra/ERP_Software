package in.retailflow.api.sales.web;

import in.retailflow.api.common.api.ApiResponse;
import in.retailflow.api.sales.dto.OutstandingRowResponse;
import in.retailflow.api.sales.dto.PaymentRequest;
import in.retailflow.api.sales.dto.PaymentResponse;
import in.retailflow.api.sales.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<List<PaymentResponse>> list(
            @RequestParam(required = false) UUID saleId,
            @RequestParam(required = false) UUID salesOrderId,
            @RequestParam(required = false) UUID customerId) {
        if (saleId != null) {
            return ApiResponse.ok(paymentService.forSale(saleId));
        }
        if (salesOrderId != null) {
            return ApiResponse.ok(paymentService.forOrder(salesOrderId));
        }
        if (customerId != null) {
            return ApiResponse.ok(paymentService.forCustomer(customerId));
        }
        return ApiResponse.ok(List.of());
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<List<OutstandingRowResponse>> outstanding(@RequestParam(defaultValue = "OUTSTANDING") String filter) {
        return ApiResponse.ok(paymentService.outstanding(filter));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','CASHIER')")
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        return ApiResponse.ok(paymentService.record(request));
    }
}
