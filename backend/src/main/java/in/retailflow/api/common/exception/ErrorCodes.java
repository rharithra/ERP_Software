package in.retailflow.api.common.exception;

public final class ErrorCodes {
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    public static final String MEMBERSHIP_NOT_FOUND = "MEMBERSHIP_NOT_FOUND";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND";
    public static final String CATEGORY_NAME_TAKEN = "CATEGORY_NAME_TAKEN";
    public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    public static final String SKU_ALREADY_EXISTS = "SKU_ALREADY_EXISTS";
    public static final String BARCODE_ALREADY_EXISTS = "BARCODE_ALREADY_EXISTS";
    public static final String OPENING_STOCK_ALREADY_RECORDED = "OPENING_STOCK_ALREADY_RECORDED";
    public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    public static final String SUPPLIER_NOT_FOUND = "SUPPLIER_NOT_FOUND";
    public static final String SUPPLIER_NAME_TAKEN = "SUPPLIER_NAME_TAKEN";
    public static final String SUPPLIER_INACTIVE = "SUPPLIER_INACTIVE";
    public static final String PURCHASE_NOT_FOUND = "PURCHASE_NOT_FOUND";
    public static final String PURCHASE_NOT_DRAFT = "PURCHASE_NOT_DRAFT";
    public static final String PURCHASE_EMPTY = "PURCHASE_EMPTY";
    public static final String PRODUCT_INACTIVE = "PRODUCT_INACTIVE";
    public static final String CUSTOMER_NOT_FOUND = "CUSTOMER_NOT_FOUND";
    public static final String CUSTOMER_PHONE_TAKEN = "CUSTOMER_PHONE_TAKEN";
    public static final String CUSTOMER_INACTIVE = "CUSTOMER_INACTIVE";
    public static final String SALE_NOT_FOUND = "SALE_NOT_FOUND";
    public static final String SALE_NOT_DRAFT = "SALE_NOT_DRAFT";
    public static final String SALE_NOT_COMPLETED = "SALE_NOT_COMPLETED";
    public static final String SALE_EMPTY = "SALE_EMPTY";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {}
}
