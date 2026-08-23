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
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {}
}
