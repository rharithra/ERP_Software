package in.retailflow.api.common.exception;

public class RetailflowException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public RetailflowException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
