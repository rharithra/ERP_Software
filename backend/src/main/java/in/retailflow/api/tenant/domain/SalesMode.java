package in.retailflow.api.tenant.domain;

import java.util.Arrays;

public enum SalesMode {
    QUICK_SALE,
    PIPELINE,
    HYBRID;

    public static SalesMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported sales mode"));
    }
}
