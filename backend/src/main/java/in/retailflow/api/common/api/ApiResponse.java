package in.retailflow.api.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message, null));
    }

    public static <T> ApiResponse<T> error(String code, String message, List<FieldErrorDetail> details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details));
    }

    public record ApiError(String code, String message, List<FieldErrorDetail> details) {}

    public record FieldErrorDetail(String field, String message) {}
}
