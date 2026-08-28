package in.retailflow.api.sales.dto;

public record SaleInvoiceResponse(
        SaleResponse sale,
        String companyName,
        String companyGstin,
        String companyAddress,
        String companyPhone) {}
