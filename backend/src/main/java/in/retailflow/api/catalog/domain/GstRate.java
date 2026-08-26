package in.retailflow.api.catalog.domain;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Indian GST slabs used on products. Future sales/invoices should reuse this enum
 * rather than hardcoding rates.
 */
public enum GstRate {
    ZERO(new BigDecimal("0")),
    FIVE(new BigDecimal("5")),
    TWELVE(new BigDecimal("12")),
    EIGHTEEN(new BigDecimal("18")),
    TWENTY_EIGHT(new BigDecimal("28"));

    private final BigDecimal percent;

    GstRate(BigDecimal percent) {
        this.percent = percent;
    }

    public BigDecimal percent() {
        return percent;
    }

    public static GstRate fromPercent(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("GST rate is required");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return Arrays.stream(values())
                .filter(rate -> rate.percent.stripTrailingZeros().compareTo(normalized) == 0)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported GST rate: " + value));
    }
}
